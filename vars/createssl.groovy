import uk.gov.hmcts.contino.*

def call(String platform, String subscription) {

  echo "Running SSL certificate creation script"

  def az = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$subscription az $cmd", returnStdout: true).trim() }

  // Generate random pw for cert file
  String pfxPass = org.apache.commons.lang.RandomStringUtils.random(9, true, true)

  def functions = libraryResource 'uk/gov/hmcts/contino/ilbSSL.sh'
  writeFile file: 'ilbSSL.sh', text: functions
  result = sh "bash ilbSSL.sh core-infra-${platform} ${pfxPass} ${platform} ${subscription}"

  // Setting environment vars consumed by TF
  az "keyvault certificate show --vault-name app-vault-${subscription} --name core-infra-${platform} --query x509ThumbprintHex --output tsv > thumbhex.txt"
  thumbprinthex = readFile('thumbhex.txt')
  env.TF_VAR_certificateThumbprint = "${thumbprinthex}"
  env.TF_VAR_certificateName = "core-infra-${platform}"
  env.TF_VAR_pfxFile = "${WORKSPACE}/core-infra-${platform}.pfx"
  env.TF_VAR_password = "${pfxPass}"
  env.TF_VAR_vaultName = "app-vault-${subscription}"
}
