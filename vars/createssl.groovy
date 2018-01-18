import uk.gov.hmcts.contino.*
import groovy.json.JsonSlurper

def call(String platform, String subscription) {

  echo "Running SSL certificate creation script"

  // Generate random pw for cert file
  String pfxPass = org.apache.commons.lang.RandomStringUtils.random(9, true, true)

  def functions = libraryResource 'uk/gov/hmcts/contino/ilbSSL.sh'
  writeFile file: 'ilbSSL.sh', text: functions
  result = sh "bash ilbSSL.sh core-infra-${platform} ${pfxPass} ${platform} ${subscription}"

  // Setting environment vars consumed by TF
  sh "az keyvault certificate show --vault-name app-vault-${subscription} --name core-infra-${platform} --query x509ThumbprintHex --output tsv > thumbhex.txt"
  thumbprinthex = readFile('thumbhex.txt')
  env.TF_VAR_certificateThumbprint = "${thumbprinthex}"
  env.TF_VAR_certificateName = "core-infra-${platform}"
  env.TF_VAR_pfxBlobString = readFile('base64.txt')
  env.TF_VAR_password = "${pfxPass}"
}
