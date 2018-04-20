#!groovy
import groovy.json.JsonSlurperClassic

def call(String environment, Closure body) {
    def response = httpRequest httpMode: 'POST', requestBody: "grant_type=client_credentials&resource=https%3A%2F%2Fmanagement.core.windows.net%2F&client_id=$env.ARM_CLIENT_ID&client_secret=$env.ARM_CLIENT_SECRET", acceptType: 'APPLICATION_JSON', url: "https://login.microsoftonline.com/$env.ARM_TENANT_ID/oauth2/token"
    TOKEN = new JsonSlurperClassic().parseText(response.content).access_token
    def vip = httpRequest httpMode: 'GET', customHeaders: [[name: 'Authorization', value: "Bearer ${TOKEN}"]], url: "https://management.azure.com/subscriptions/$env.ARM_SUBSCRIPTION_ID/resourceGroups/core-infra-$environment/providers/Microsoft.Web/hostingEnvironments/core-compute-$environment/capacities/virtualip?api-version=2016-09-01"
    def internalip = new JsonSlurperClassic().parseText(vip.content).internalIpAddress
    log.info("Internal IP: $internalip")
    env.TF_VAR_ilbIp = internalip

    body.call()

    env.TF_VAR_ilbIp = null
}
