
def call(String product, String environment, Map<String, String> vaultOverrides, Closure body) {
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
    '${product}': [
      secret('definition-importer-username', 'DEFINITION_IMPORTER_USERNAME'),
      secret('definition-importer-password', 'DEFINITION_IMPORTER_PASSWORD')
    ]
  ]

  executeClosure(secrets.entrySet().iterator(), product, dependedEnv) {
    body.call()
  }
}

def executeClosure(Iterator<Map.Entry<String,List<Map<String,Object>>>> secretIterator, String product, String dependedEnv, String highLevelDataSetupKeyVaultName, Closure body) {
  def entry = secretIterator.next()

  // ${product} is a placeholder for the team's vault
  // this assumes that the team, a) has a product vault, b) is named the same as their product
  def productName = entry.key != '${product}' ? entry.key : product
    String theKeyVaultUrl = ""
    if (highLevelDataSetupKeyVaultName.isEmpty() || highLevelDataSetupKeyVaultName.isBlank()) {
        theKeyVaultUrl = "https://${productName}-${dependedEnv}.vault.azure.net/"
    }
    else {
        theKeyVaultUrl = "https://${highLevelDataSetupKeyVaultName}-${dependedEnv}.vault.azure.net/"
    }

  withAzureKeyvault(
    azureKeyVaultSecrets: entry.value,
    keyVaultURLOverride: theKeyVaultUrl
  ) {
    if (secretIterator.hasNext()) {
      return executeClosure(secretIterator, product, dependedEnv, body)
    } else {
      body.call()
    }
  }
}

static LinkedHashMap<String, Object> secret(String secretName, String envVar) {
  [$class     : 'AzureKeyVaultSecret',
   secretType : 'Secret',
   name       : secretName,
   version    : '',
   envVariable: envVar
  ]
}
