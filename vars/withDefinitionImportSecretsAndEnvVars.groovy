
import uk.gov.hmcts.pipeline.AgentSelector

def call(String vaultName, String environment, Map<String, String> vaultOverrides, Closure body) {
  def product = env.PRODUCT ?: env.RAW_PRODUCT_NAME ?: ''
  call(vaultName, environment, vaultOverrides, product, body)
}

def call(String vaultName, String environment, Map<String, String> vaultOverrides, String product, Closure body) {
  def dependedEnv = vaultOverrides.get(environment, environment)

  env.IDAM_API_URL_BASE = "https://idam-api.${dependedEnv}.platform.hmcts.net"
  env.S2S_URL_BASE = "http://rpe-service-auth-provider-${dependedEnv}.service.core-compute-${dependedEnv}.internal"
  env.CCD_API_GATEWAY_S2S_ID = "ccd_gw"
  env.CCD_API_GATEWAY_OAUTH2_CLIENT_ID = "ccd_gateway"
  env.CCD_API_GATEWAY_OAUTH2_REDIRECT_URL = "https://www-ccd.${dependedEnv}.platform.hmcts.net/oauth2redirect"
  env.DEFINITION_STORE_URL_BASE = env.DEFINITION_STORE_URL_BASE ?: "http://ccd-definition-store-api-${dependedEnv}.service.core-compute-${dependedEnv}.internal"

  prodName = env.PROD_ENVIRONMENT_NAME ?: 'prod'
  if (dependedEnv == prodName) {
    env.CCD_API_GATEWAY_OAUTH2_REDIRECT_URL = "https://www.ccd.platform.hmcts.net/oauth2redirect"
    env.IDAM_API_URL_BASE = "https://idam-api.platform.hmcts.net"
    env.DEFINITION_STORE_URL_BASE = "http://ccd-definition-store-api-prod.service.core-compute-prod.internal"
  }

  def secrets = [
    'ccd': [
      secret('ccd-api-gateway-oauth2-client-secret', 'CCD_API_GATEWAY_OAUTH2_CLIENT_SECRET')
    ],
    's2s': [
      secret('microservicekey-ccd-gw', 'CCD_API_GATEWAY_S2S_KEY')
    ],
    '${vaultName}': [
      secret('definition-importer-username', 'DEFINITION_IMPORTER_USERNAME'),
      secret('definition-importer-password', 'DEFINITION_IMPORTER_PASSWORD')
    ]
  ]

  executeClosure(secrets.entrySet().iterator(), vaultName, dependedEnv, product) {
    body.call()
  }
}

def executeClosure(Iterator<Map.Entry<String,List<Map<String,Object>>>> secretIterator, String vaultName, String dependedEnv, String product, Closure body) {
  def entry = secretIterator.next()

  def productName = entry.key != '${vaultName}' ? entry.key : vaultName

  String theKeyVaultUrl = "https://ektestkv2-${dependedEnv}.vault.azure.net/"

  if (AgentSelector.isRunningOnEnvironmentAgent(env, null, product)) {
    withEnvironmentManagedIdentitySecrets(entry.value, theKeyVaultUrl) {
      if (secretIterator.hasNext()) {
        return executeClosure(secretIterator, vaultName, dependedEnv, product, body)
      } else {
        body.call()
      }
    }
    return
  }

  withAzureKeyvault(
    azureKeyVaultSecrets: entry.value,
    keyVaultURLOverride: theKeyVaultUrl
  ) {
    if (secretIterator.hasNext()) {
      return executeClosure(secretIterator, vaultName, dependedEnv, product, body)
    } else {
      body.call()
    }
  }
}

private void withEnvironmentManagedIdentitySecrets(List<Map<String, Object>> secrets, String keyVaultUrl, Closure body) {
  String vaultName = vaultNameFromUrl(keyVaultUrl)
  String azureConfigName = AgentSelector.normaliseEnvironment(env.DEPLOYMENT_ENVIRONMENT)
  String azureConfigDir = "/opt/jenkins/.azure-${azureConfigName}"

  loginWithManagedIdentity(azureConfigDir)

  List<String> variables = secrets.collect { Map<String, Object> secret ->
    String secretName = secret.name.toString()
    String envVariable = secret.envVariable.toString()
    String secretValue = readKeyVaultSecret(vaultName, secretName, azureConfigDir).trim()

    "${envVariable}=${secretValue}"
  }

  withEnv(variables) {
    body.call()
  }
}

private String readKeyVaultSecret(String vaultName, String secretName, String azureConfigDir) {
  sh(
    label: "az keyvault secret show ${vaultName}/${secretName}",
    script: """
      set +x
      error_file=\$(mktemp)
      secret_value=\$(env AZURE_CONFIG_DIR='${shellQuote(azureConfigDir)}' az keyvault secret show --vault-name '${shellQuote(vaultName)}' --name '${shellQuote(secretName)}' --query value -o tsv 2>"\$error_file")
      exit_code=\$?

      if [ "\$exit_code" -ne 0 ]; then
        cat "\$error_file" >&2
        rm -f "\$error_file"
        exit "\$exit_code"
      fi

      rm -f "\$error_file"
      printf '%s' "\$secret_value"
    """.stripIndent().trim(),
    returnStdout: true
  )
}

private void loginWithManagedIdentity(String azureConfigDir) {
  sh(
    script: """
      set +x
      env AZURE_CONFIG_DIR='${shellQuote(azureConfigDir)}' az login --identity >/dev/null
    """.stripIndent().trim()
  )
}

private String vaultNameFromUrl(String keyVaultUrl) {
  return keyVaultUrl
    .replaceFirst(/^https:\/\//, '')
    .replaceFirst(/\.vault\.azure\.net\/?$/, '')
}

private String shellQuote(String value) {
  return value.replace("'", "'\"'\"'")
}

static LinkedHashMap<String, Object> secret(String secretName, String envVar) {
  [$class     : 'AzureKeyVaultSecret',
   secretType : 'Secret',
   name       : secretName,
   version    : '',
   envVariable: envVar
  ]
}
