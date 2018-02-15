#!groovy

def call(String vaultName, List<Map<String, Object>> vaultSecrets, Closure body) {
  ansiColor('xterm') {
    withCredentials([azureServicePrincipal(
      credentialsId: "jenkinsServicePrincipal",
      subscriptionIdVariable: 'JENKINS_SUBSCRIPTION_ID',
      clientIdVariable: 'JENKINS_CLIENT_ID',
      clientSecretVariable: 'JENKINS_CLIENT_SECRET',
      tenantIdVariable: 'JENKINS_TENANT_ID')]) {

      sh 'az login --service-principal -u $JENKINS_CLIENT_ID -p $JENKINS_CLIENT_SECRET -t $JENKINS_TENANT_ID'
      sh 'az account set --subscription $JENKINS_SUBSCRIPTION_ID'

      log.info "using $vaultName"

      def envSecrets = []
      for (Map<String, Object> secret : vaultSecrets) {
        def secretValue = sh(script: "az keyvault secret show --vault-name '$vaultName' --name '${secret.name}' --query value", returnStdout: true).trim()
        envSecrets.add("${secret.envVar}=${secretValue}")
      }

      withEnv(envSecrets)
      {
        body.call()
      }
    }
  }
}
