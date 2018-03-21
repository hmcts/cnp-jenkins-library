#!groovy
/* Must be used inside a withSubscription block */
def call(String environment) {

  echo "Retrieving certificate for ${environment} from vault"
  def az = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-${env.SUBSCRIPTION_NAME} az $cmd", returnStdout: true).trim() }

  log.info "using ${env.INFRA_VAULT_NAME}"

  // check certificate exists
  az "keyvault certificate show --vault-name $env.INFRA_VAULT_NAME --name core-compute-${environment}"

  // Setting environment vars consumed by TF
  env.TF_VAR_certificateName = "core-compute-${environment}"
  thumbPrint = az "keyvault certificate show --vault-name $env.INFRA_VAULT_NAME --name ${env.TF_VAR_certificateName} --query x509ThumbprintHex --output tsv"
  if (thumbPrint.contains("not found"))
  {
    defaultPolicy = libraryResource 'uk/gov/hmcts/contino/certificateDefaultPolicy.json'
    log.info("Certificate name ${env.TF_VAR_certificateName} does not exist in vault ${env.INFRA_VAULT_NAME}! Creating one right now...")
    az(/keyvault certificate create --vault-name ${env.INFRA_VAULT_NAME} --name ${env.TF_VAR_certificateName} --policy "${defaultPolicy}"/)
    log.info("Retrieving the thumbprint")
    env.TF_VAR_certificateThumbprint = az "keyvault certificate show --vault-name $env.INFRA_VAULT_NAME --name ${env.TF_VAR_certificateName} --query x509ThumbprintHex --output tsv"
  }
  else
    env.TF_VAR_certificateThumbprint = thumbPrint

}
