
def call(String product, String environment, String subscription) {

  node {
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
