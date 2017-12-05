import uk.gov.hmcts.contino.*
import groovy.json.JsonSlurper

def call(String platform) {



  def response = httpRequest httpMode: 'POST', requestBody: "grant_type=client_credentials&resource=https%3A%2F%2Fmanagement.core.windows.net%2F&client_id=$ARM_CLIENT_ID&client_secret=$ARM_CLIENT_SECRET", acceptType: 'APPLICATION_JSON', url: "https://login.microsoftonline.com/$ARM_TENANT_ID/oauth2/token"
  TOKEN = new JsonSlurper().parseText(response.content).access_token
  def vip = httpRequest httpMode: 'GET', customHeaders: [[name: 'Authorization', value: "Bearer ${TOKEN}"]], url: "https://management.azure.com/subscriptions/$ARM_SUBSCRIPTION_ID/resourceGroups/core-infra-${platform}/providers/Microsoft.Web/hostingEnvironments/core-compute-${platform}/capacities/virtualip?api-version=2016-09-01"
  def internalip = new JsonSlurper().parseText(vip.content).internalIpAddress
  println internalip

  echo "Running SSL certificate creation script"
  String pfxPass = org.apache.commons.lang.RandomStringUtils.random(9, true, true)

  def functions = libraryResource 'uk/gov/hmcts/contino/ilbSSL.sh'
  writeFile file: 'ilbSSL.sh', text: functions

  result = sh "bash ilbSSL.sh core-infra-${platform} ${pfxPass} ${platform}"

  sh "az keyvault certificate show --vault-name ${platform}-infra-vault --name core-infra-${platform} --query x509Thumbprint --output tsv > thumb.txt"

  thumbprint = readFile 'thumb.txt'

  env.TF_VAR_certificateThumbprint = "${thumbprint}"
  env.TF_VAR_certificateName = "core-infra-${platform}"
}
