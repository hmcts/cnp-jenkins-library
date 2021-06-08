
def call(String product, String environment, Map<String, String> vaultOverrides, Closure block) {
  def dependedEnv = vaultOverrides.get(environment, environment)

  env.IDAM_URL_BASE = "https://idam-api.${dependedEnv}.platform.hmcts.net"
  env.S2S_URL_BASE = "http://rpe-service-auth-provider-${dependedEnv}.service.core-compute-${dependedEnv}.internal"
  env.DEFINITION_STORE_URL_BASE = "http://ccd-definition-store-api-${dependedEnv}.service.core-compute-${dependedEnv}.internal"
  env.CCD_API_GATEWAY_S2S_ID = "ccd_gw"
  env.CCD_API_GATEWAY_OAUTH2_CLIENT_ID = "ccd_gateway"
  env.CCD_API_GATEWAY_OAUTH2_REDIRECT_URL = "https://www-ccd.${dependedEnv}.platform.hmcts.net/oauth2redirect"

  prodName = env.PROD_ENVIRONMENT_NAME ?: 'prod'
  if (dependedEnv == prodName) {
    env.CCD_API_GATEWAY_OAUTH2_REDIRECT_URL = "https://www.ccd.platform.hmcts.net/oauth2redirect"
    env.IDAM_URL = "https://idam-api.platform.hmcts.net"
  }

  def secrets = [
    [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'definition-importer-username', version: '', envVariable: 'DEFINITION_IMPORTER_USERNAME'],
    [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'definition-importer-password', version: '', envVariable: 'DEFINITION_IMPORTER_PASSWORD'],
    [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'ccd-api-gateway-oauth2-client-secret', version: '', envVariable: 'CCD_API_GATEWAY_OAUTH2_CLIENT_SECRET'],
    [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'microservicekey-ccd-api-gateway', version: '', envVariable: 'CCD_API_GATEWAY_S2S_KEY'],
  ]

  String theKeyVaultUrl = "https://${product}-${dependedEnv}.vault.azure.net/"

  withAzureKeyvault(
    azureKeyVaultSecrets: secrets,
    keyVaultURLOverride: theKeyVaultUrl
  ) {
    block.call()
  }
}
