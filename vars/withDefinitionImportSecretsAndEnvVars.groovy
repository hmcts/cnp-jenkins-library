import uk.gov.hmcts.contino.AppPipelineConfig

def call(String vaultName, String environment, AppPipelineConfig config, Closure body) {
  def secrets = config.vaultSecrets
  def dependedEnv = config.vaultEnvironmentOverrides.get(environment, environment)
  echo "secrets configured  ...... $secrets['${vaultName}']"
  echo "Vault Name   ...... ${vaultName}"

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

  def hldsSecrets = [
    'ccd': [
      secret('ccd-api-gateway-oauth2-client-secret', 'CCD_API_GATEWAY_OAUTH2_CLIENT_SECRET')
    ],
    's2s': [
      secret('microservicekey-ccd-gw', 'CCD_API_GATEWAY_S2S_KEY')
    ],
    '${vaultName}': [
      secret('definition-importer-username', 'DEFINITION_IMPORTER_USERNAME'),
      secret('definition-importer-password', 'DEFINITION_IMPORTER_PASSWORD'),
      secret('ccd-role-assignment-solicitor-email','ROLE_ASSIGNMENT_USER_EMAIL'),
      secret('ccd-role-assignment-solicitor-password','ROLE_ASSIGNMENT_USER_PASSWORD'),

      secret('ccd-befta-master-caseworker-staff-pwd','ROLE_ASSIGNMENT_CASEWORKER_STAFF_PWD'),
      secret('ccd-befta-master-caseworker-regional-staff-pwd','ROLE_ASSIGNMENT_CASEWORKER_REGIONAL_STAFF_PWD'),
      secret('microservicekey-ccd-data', 'ROLE_ASSIGNMENT_API_GATEWAY_S2S_CLIENT_KEY')
    ]
  ]

  executeClosure(hldsSecrets.entrySet().iterator(), vaultName, dependedEnv) {
    body.call()
  }
}

def executeClosure(Iterator<Map.Entry<String,List<Map<String,Object>>>> secretIterator, String vaultName, String dependedEnv, Closure body) {
  def entry = secretIterator.next()

  def productName = entry.key != '${vaultName}' ? entry.key : vaultName

  String theKeyVaultUrl = "https://${productName}-${dependedEnv}.vault.azure.net/"

  withAzureKeyvault(
    azureKeyVaultSecrets: entry.value,
    keyVaultURLOverride: theKeyVaultUrl
  ) {
    if (secretIterator.hasNext()) {
      return executeClosure(secretIterator, vaultName, dependedEnv, body)
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
