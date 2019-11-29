#!groovy
import groovy.json.JsonSlurperClassic

def call(String environment, Closure body) {
    TOKEN = sh(script: "az account get-access-token --query accessToken -o tsv", returnStdOut: true).trim()
    def vip = httpRequest httpMode: 'GET', customHeaders: [[name: 'Authorization', value: "Bearer ${TOKEN}"]], url: "https://management.azure.com/subscriptions/$env.ARM_SUBSCRIPTION_ID/resourceGroups/core-infra-$environment/providers/Microsoft.Web/hostingEnvironments/core-compute-$environment/capacities/virtualip?api-version=2016-09-01"
    def internalip = new JsonSlurperClassic().parseText(vip.content).internalIpAddress
    log.info("Internal IP: $internalip")
    env.TF_VAR_ilbIp = internalip

    body.call()

    env.TF_VAR_ilbIp = null
}
