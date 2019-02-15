
@Deprecated
def call(String product, String environment, String subscription, String deploymentTarget = "") {

  def environmentDeploymentTarget = "$environment$deploymentTarget"

  node {
    env.PATH = "$env.PATH:/usr/local/bin"

    stage('Checkout') {
      deleteDir()
      checkout scm
    }
    withSubscription(subscription) {
      withIlbIp(environmentDeploymentTarget) {
        spinInfra(product, environment, false, subscription, deploymentTarget)
      }
    }
  }
}
