#!groovy
/* Must be used inside a withSubscription block */
def call(String environment) {

  echo "Producing certificate for ${environment}"
  def az = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-${env.SUBSCRIPTION_NAME} az ${cmd}", returnStdout: true).trim() }

  def infraVaultName = "infra-vault-${env.SUBSCRIPTION_NAME}"
  log.info "using $infraVaultName"
  env.TF_VAR_vaultName = infraVaultName
  // Setting environment vars consumed by TF
  env.TF_VAR_certificateName = "core-compute-${environment}"
  thumbPrint = az(/keyvault certificate list --vault-name $infraVaultName --query "[?contains(id,'${env.TF_VAR_certificateName}')].x509ThumbprintHex" -o tsv/)
  // check certificate exists
  if (thumbPrint) {
    log.info("Certificate found in vault... will use the same")
    env.TF_VAR_certificateThumbprint = thumbPrint
  }
  else
  {
    defaultPolicy = libraryResource 'uk/gov/hmcts/contino/certificateDefaultPolicy.json'
    writeFile file: 'certificateDefaultPolicy.json', text: defaultPolicy

    log.info("Certificate name ${env.TF_VAR_certificateName} does not exist in vault $infraVaultName! Creating one right now...")
    az(/keyvault certificate create --vault-name $infraVaultName --name ${env.TF_VAR_certificateName} --policy @certificateDefaultPolicy.json/)
    log.info("Retrieving the thumbprint")
    env.TF_VAR_certificateThumbprint = az "keyvault certificate show --vault-name $infraVaultName --name ${env.TF_VAR_certificateName} --query x509ThumbprintHex -o tsv"
  }

}
