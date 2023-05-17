import uk.gov.hmcts.contino.AppPipelineConfig

def call(String vaultName, String environment, AppPipelineConfig config, Closure body) {
  def secrets = config.vaultSecrets
  echo ("secrets   ...... $secrets")
  echo ("Vault Name   ...... ${vaultName}")
  echo ("env Name   ...... ${environment}")
  def dependedEnv = config.vaultEnvironmentOverrides.get(environment, environment)
  echo ("dependedEnv Name   ...... ${dependedEnv}")

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
    '${vaultName}':  [
      secret('definition-importer-username', 'DEFINITION_IMPORTER_USERNAME'),
      secret('definition-importer-password', 'DEFINITION_IMPORTER_PASSWORD')
    ]
  ]

  if (!secrets.isEmpty()) {
    echo "New Secrets Provided.."
    overrideHldsSecrets(hldsSecrets, secrets.entrySet().iterator())
  } else {
    echo "No Secrets Provided.."
  }

  echo "final secrets  ...... $hldsSecrets"

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

def overrideHldsSecrets(Map<String, List<Map<String, Object>>> hldsSecrets, Iterator<Map.Entry<String, List<Map<String, Object>>>> secretIterator) {
  def entry = secretIterator.next()
  String entryKey = entry.key
  if (entry.key.contains('-${env}')) {
    // Some entries may have the env suffix. We strip that out here
    entryKey = entry.key.replace('-${env}', '')
  }

  if (!hldsSecrets.keySet().contains(entryKey)) {
    hldsSecrets.putIfAbsent(entryKey, entry.value)
  } else {
    echo ("Overriding secrets in item: " + entryKey)
    def existingItems = hldsSecrets.get(entryKey)
    List<Map<String, Object>> finalSecrets = new ArrayList<>()

    // Compare the new secrets with the existing ones. Add the new ones to the final list
    for (Map<String, Object> secretValue : entry.value) {
      for (Map<String, Object> existingItem : existingItems) {
        if (secretValue["envVariable"].toString() == existingItem["envVariable"].toString()) {
          finalSecrets.add(secretValue)
          break
        }
      }
      if (!finalSecrets.contains(secretValue)) {
        finalSecrets.add(secretValue)
      }
    }

    // Add the existing hldsSecrets if they are not in the final list
    for (Map<String, Object> existingItem : existingItems) {
      if (!finalSecrets["envVariable"].contains(existingItem["envVariable"]) && !finalSecrets.contains(existingItem)) {
        finalSecrets.add(existingItem)
      }}
    hldsSecrets.replace(entryKey, finalSecrets as List<Map<String, Object>>)
  }

  if (secretIterator.hasNext()) {
    overrideHldsSecrets(hldsSecrets, secretIterator)
  }
}
