import uk.gov.hmcts.contino.azure.KeyVault
import uk.gov.hmcts.contino.azure.ProductVaultEntries

@Deprecated
def call(String product, String environment, String subscription) {
  call(product, environment, subscription, false)
}

@Deprecated
def call(String product, String environment, String subscription, boolean planOnly) {
  call(product, environment, subscription, planOnly, null)
}

@Deprecated
def call(String product, String environment, String subscription, boolean planOnly, String deploymentTarget) {
  echo '''
================================================================================
sharedInfrastructurePipeline is
______                              _           _ _
|  _  \\                            | |         | | |
| | | |___ _ __  _ __ ___  ___ __ _| |_ ___  __| | |
| | | / _ \\ '_ \\| '__/ _ \\/ __/ _` | __/ _ \\/ _` | |
| |/ /  __/ |_) | | |  __/ (_| (_| | ||  __/ (_| |_|
|___/ \\___| .__/|_|  \\___|\\___\\__,_|\\__\\___|\\__,_(_)
          | |
          |_|
 Use withInfraPipeline instead
 https://github.com/hmcts/cnp-jenkins-library#opinionated-infrastructure-pipeline
================================================================================
'''
  error "This is no longer in use"
}
