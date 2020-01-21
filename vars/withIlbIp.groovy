#!groovy
import groovy.json.JsonSlurperClassic

def call(String subscription, String environment, Closure body) {
  def az = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$subscription az $cmd", returnStdout: true).trim() }

  def token = az "account get-access-token --query accessToken -o tsv"
  def vip = httpRequest httpMode: 'GET', customHeaders: [[name: 'Authorization', value: "Bearer ${token}"]], url: "https://management.azure.com/subscriptions/$env.ARM_SUBSCRIPTION_ID/resourceGroups/core-infra-$environment/providers/Microsoft.Web/hostingEnvironments/core-compute-$environment/capacities/virtualip?api-version=2016-09-01"
  def internalip = new JsonSlurperClassic().parseText(vip.content).internalIpAddress
  log.info("Internal IP: $internalip")
  env.TF_VAR_ilbIp = internalip

  body.call()

  env.TF_VAR_ilbIp = null
}
