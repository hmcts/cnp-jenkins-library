def call(body) {
  // evaluate the body block, and collect configuration into the object
  /*def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config*/

  // now build, based on the configuration provided
  ansiColor('xterm') {
    terraformSetup()

    withCredentials([string(credentialsId: 'sp_password', variable: 'ARM_CLIENT_SECRET'),
                     string(credentialsId: 'tenant_id', variable: 'ARM_TENANT_ID'),
                     string(credentialsId: 'subscription_id', variable: 'ARM_SUBSCRIPTION_ID'),
                     string(credentialsId: 'object_id', variable: 'ARM_CLIENT_ID'),
                     string(credentialsId: 'kitchen_github', variable: 'TOKEN'),
                     string(credentialsId: 'kitchen_github', variable: 'TF_VAR_token'),
                     string(credentialsId: 'kitchen_client_secret', variable: 'AZURE_CLIENT_SECRET'),
                     string(credentialsId: 'kitchen_tenant_id', variable: 'AZURE_TENANT_ID'),
                     string(credentialsId: 'kitchen_subscription_id', variable: 'AZURE_SUBSCRIPTION_ID'),
                     string(credentialsId: 'kitchen_client_id', variable: 'AZURE_CLIENT_ID')]) {

      sh "echo 'Reform Platform Pipeline Initialized...'"

      withEnv(["GIT_COMMITTER_NAME=jenkinsmoj", "GIT_COMMITTER_EMAIL=jenkinsmoj@contino.io"]) {
        body.call()
      }
    }
  }


}
