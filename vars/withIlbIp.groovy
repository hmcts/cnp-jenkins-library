#!groovy
import groovy.json.JsonSlurperClassic

def call(String env, Closure body) {
    def response = httpRequest httpMode: 'POST', requestBody: "grant_type=client_credentials&resource=https%3A%2F%2Fmanagement.core.windows.net%2F&client_id=$ARM_CLIENT_ID&client_secret=$ARM_CLIENT_SECRET", acceptType: 'APPLICATION_JSON', url: "https://login.microsoftonline.com/$ARM_TENANT_ID/oauth2/token"
    TOKEN = new JsonSlurperClassic().parseText(response.content).access_token
    def vip = httpRequest httpMode: 'GET', customHeaders: [[name: 'Authorization', value: "Bearer ${TOKEN}"]], url: "https://management.azure.com/subscriptions/$ARM_SUBSCRIPTION_ID/resourceGroups/core-infra-$env/providers/Microsoft.Web/hostingEnvironments/core-compute-$env/capacities/virtualip?api-version=2016-09-01"
    def internalip = new JsonSlurperClassic().parseText(vip.content).internalIpAddress
    println internalip
    env.TF_VAR_ilbIp = internalip

    body.call()

    env.TF_VAR_ilbIp = null
}
