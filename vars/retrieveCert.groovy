#!groovy
/* Must be used inside a withSubscription block */
def call(String environment) {

  echo "Producing certificate for ${environment}"
  def az = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-${env.SUBSCRIPTION_NAME} az ${cmd}", returnStdout: true).trim() }

  log.info "using ${env.INFRA_VAULT_NAME}"
  env.TF_VAR_vaultName = env.INFRA_VAULT_NAME
  // Setting environment vars consumed by TF
  env.TF_VAR_certificateName = "core-compute-${environment}"
  thumbPrint = az(/keyvault certificate list --vault-name ${env.INFRA_VAULT_NAME} --query "[?contains(id,'${env.TF_VAR_certificateName}')].x509ThumbprintHex" -o tsv/)
  // check certificate exists
  if (thumbPrint) {
    log.info("Certificate found in vault... will use the same")
    env.TF_VAR_certificateThumbprint = thumbPrint
  }
  else
  {
    defaultPolicy = libraryResource 'uk/gov/hmcts/contino/certificateDefaultPolicy.json'
    writeFile file: 'certificateDefaultPolicy.json', text: defaultPolicy

    log.info("Certificate name ${env.TF_VAR_certificateName} does not exist in vault ${env.INFRA_VAULT_NAME}! Creating one right now...")
    az(/keyvault certificate create --vault-name ${env.INFRA_VAULT_NAME} --name ${env.TF_VAR_certificateName} --policy @certificateDefaultPolicy.json/)
    log.info("Retrieving the thumbprint")
    env.TF_VAR_certificateThumbprint = az "keyvault certificate show --vault-name $env.INFRA_VAULT_NAME --name ${env.TF_VAR_certificateName} --query x509ThumbprintHex -o tsv"
  }

}
