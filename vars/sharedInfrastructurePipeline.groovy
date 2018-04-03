
def call(String product, String environment, String subscription) {

  node {
    env.PATH = "$env.PATH:/usr/local/bin"

    def tfOutput

    stage('Checkout') {
      deleteDir()
      checkout scm
    }

    withSubscription(subscription) {
      withIlbIp(environment) {
        tfOutput = spinInfra(product, environment, false, subscription)
      }

      stage('Store shared product secrets') {
        def az = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$subscription az $cmd", returnStdout: true).trim() }

        az "keyvault secret set --vault-name '${tfOutput.vaultName}' --name 'AppInsightsInstrumentationKey' --value '${tfOutput.appInsightsInstrumentationKey}'".toString()
      }
    }

  }
}
