
def call(String product, String environment, String subscription) {

  node {
    env.PATH = "$env.PATH:/usr/local/bin"
    stage('Checkout') {
      deleteDir()
      checkout scm
    }
    withSubscription(subscription) {
      withIlbIp(environment) {
        spinInfra(product, environment, false, subscription)
      }
    }
  }
}
