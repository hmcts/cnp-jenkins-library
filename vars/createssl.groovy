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

  result = sh "bash ilbSSL.sh core-infra-${platform} ${pfxPass}"

  sh "az keyvault certificate import --vault-name ${platform}-infra-vault -n core-infra-${platform} -f core-infra-${platform}.pfx --password $pfxPass"

  sh"az network application-gateway auth-cert create --cert-file core-infra-${platform}.cer --gateway-name core-infra-${platform} --name core-infra-${platform} --resource-group core-infra-${platform}"

}
