
@Deprecated
def call(String product, String environment, String subscription, String deploymentTarget = "") {

  echo '''
================================================================================
withInfrastructurePipeline is
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

  def environmentDeploymentTarget = "$environment$deploymentTarget"

  try {
    node {
      env.PATH = "$env.PATH:/usr/local/bin"

      stage('Checkout') {
        checkoutScm()
      }
      withSubscription(subscription) {
        spinInfra(product, null, environment, false, subscription, deploymentTarget)
      }
    }
  } finally {
    deleteDir()
  }
}
