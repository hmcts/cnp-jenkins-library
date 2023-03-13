import uk.gov.hmcts.contino.AppPipelineConfig

def call(String vaultName, String environment, AppPipelineConfig config, Closure body) {
  Map<String, List<Map<String, Object>>> secrets = config.vaultSecrets
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

  Map<String, List<Map<String, Object>>> hldsSecrets = [
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
    secrets = compareMappedSecrets(hldsSecrets, secrets)
  } else {
    secrets = hldsSecrets
  }
  echo("final secrets   ...... $secrets")

  executeClosure(secrets.entrySet().iterator(), vaultName, dependedEnv) {
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

static def compareMappedSecrets(Map<String, List<Map<String, Object>>> defaultSecretsGroup, Map<String, List<Map<String, Object>>> newSecretsGroup) {
  List<Map<String, Object>> finalSecretsList = new ArrayList()
  defaultSecretsGroup.iterator().forEachRemaining {
    def currentDefaultItem = it
    newSecretsGroup.putIfAbsent(currentDefaultItem.key, currentDefaultItem.getValue())
    if (currentDefaultItem.value != newSecretsGroup.get(currentDefaultItem.key)) {
      finalSecretsList = compareListOfMappedSecrets(currentDefaultItem.value, newSecretsGroup.get(currentDefaultItem.key))
      newSecretsGroup.replace(currentDefaultItem.key, finalSecretsList)
    }
  }
  return newSecretsGroup
}

// compare values inside the map
static List<Map<String, Object>> compareListOfMappedSecrets(List<Map<String, Object>> defaultSecretsList, List<Map<String, Object>> newSecretsList) {
  List<Map<String, Object>> finalList = new ArrayList<>()
  defaultSecretsList.iterator().forEachRemaining {
    def defaultSecret = it
    newSecretsList.forEach {
      def newSecret = it
      if (defaultSecret != newSecret) {
        if (defaultSecret.get("envVariable").toString() != newSecret.get("envVariable").toString()) {
          finalList.addAll(newSecret, defaultSecret)
        } else if (!finalList.contains(newSecret)) {
          finalList.add(newSecret)
        }
      } else {
        finalList.add(newSecret)
      }
    }
  }
  return finalList
}

