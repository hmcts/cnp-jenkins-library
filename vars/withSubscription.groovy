import groovy.json.JsonSlurperClassic

def call(String servicePrincipal, String vaultName, Clojure body) {

  withCredentials([azureServicePrincipal(servicePrincipal)]) {

    resp = sh(script: "az keyvault secret list --vault-name '${vaultName}'", returnStdout: true).trim()
    //resp = sh(script: "az login --service-principal -u $AZURE_CLIENT_ID -p $AZURE_CLIENT_SECRET -t $AZURE_TENANT_ID", returnStdout: true).trim()
    secrets = new JsonSlurperClassic().parseText(resp)
    echo "TOKEN: '${secrets}'"

    body.call()

  }

//  steps.withCredentials([azureServicePrincipal('jenkinsSP')]) {
//    def subscription = 'nonprod'
//    if (env == 'prod')
//      subscription = 'prod'
//
//    resp = steps.sh(script: "az keyvault secret show --vault-name 'jenkins-vault' --name ${subscription}-${varName}", returnStdout: true).trim()
//    secret = new JsonSlurperClassic().parseText(resp)
//  }
//  return secret.value

}
