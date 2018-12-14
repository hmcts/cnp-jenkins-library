import groovy.json.JsonOutput

def call(subscription, serviceName, serviceIP) {

  println "Registering AKS application to consul cluster in `$subscription` subscription"

  def environment = subscription == 'nonprod' ? 'preview' : 'saat'

  log.info("get a token for Management API...")
  def accessTokenResponse = httpRequest(
    httpMode: 'POST',
    customHeaders: [[name: 'ContentType', value: "application/x-www-form-urlencoded"]],
    requestBody: "grant_type=client_credentials&resource=https%3A%2F%2Fmanagement.azure.com%2F&client_id=$env.ARM_CLIENT_ID&client_secret=$env.ARM_CLIENT_SECRET",
    acceptType: 'APPLICATION_JSON',
    url: "https://login.microsoftonline.com/$env.ARM_TENANT_ID/oauth2/token")
  authtoken  = readJSON(text: accessTokenResponse.content).access_token

  log.info "Getting consul's IP address ..."
  def lbCfg = httpRequest(
    httpMode: 'GET',
    customHeaders: [[name: 'Authorization', value: "Bearer ${authtoken}"]],
    url: "https://management.azure.com/subscriptions/" + env.ARM_SUBSCRIPTION_ID + "/resourceGroups/core-infra-" + environment + "/providers/Microsoft.Network/loadBalancers/consul-server_dns/frontendIPConfigurations/privateIPAddress?api-version=2018-04-01")
  if (!lbCfg.content?.trim()) {
    log.debug(lbCfg.content)
    error("Something went wrong finding consul lb")
  }

  String consulApiAddr = readJSON(text: lbCfg.content).properties.privateIPAddress
  log.info("Consul LB IP: ${consulApiAddr}")

  scmjson = JsonOutput.toJson(
    ["Name": serviceName,
     "Service": serviceName,
     "Address": "${serviceIP}",
     "Port": 80
    ])
  log.info("Registering to consul with following record: $scmjson")

  httpRequest(
    httpMode: 'PUT',
    acceptType: 'APPLICATION_JSON',
    contentType: 'APPLICATION_JSON',
    url: "http://${consulApiAddr}:8500/v1/agent/service/register",
    requestBody: "${scmjson}",
    consoleLogResponseBody: true,
    validResponseCodes: '200'
  )

}

