import groovy.json.JsonOutput

def environment(s) {
  def env
  switch (s) {
    case ['nonprod', 'prod']:
      env='preview'
      break
    case 'sandbox':
    default:
      env='saat'
      break
  }
  env
}

def call(subscription, serviceName, serviceIP) {

  println "Registering AKS application to consul cluster in `$subscription` subscription"

  log.info("get a token for Management API...")
  //get an auth TOKEN for management API to query for the ILBs
  def response = httpRequest(
    httpMode: 'POST',
    customHeaders: [[name: 'ContentType', value: "application/x-www-form-urlencoded"]],
    requestBody: "grant_type=client_credentials&resource=https%3A%2F%2Fmanagement.azure.com%2F&client_id=$env.ARM_CLIENT_ID&client_secret=$env.ARM_CLIENT_SECRET",
    acceptType: 'APPLICATION_JSON',
    url: "https://login.microsoftonline.com/$env.ARM_TENANT_ID/oauth2/token")
  authtoken  = readJSON(text: response.content).access_token

  // Get details about the consul load balancer IP address
  log.info "Getting consul's IP address ..."
  def lbCfg = httpRequest(
    httpMode: 'GET',
    customHeaders: [[name: 'Authorization', value: "Bearer ${authtoken}"]],
    url: "https://management.azure.com/subscriptions/" + env.ARM_SUBSCRIPTION_ID + "/resourceGroups/core-infra-" + environment(subscription) + "/providers/Microsoft.Network/loadBalancers/consul-server_dns/frontendIPConfigurations/privateIPAddress?api-version=2018-04-01")
  if (! lbCfg.content?.trim()) {
    log.debug(lbCfg.content)
    error("Something went wrong finding consul lb")
  }
  lbCfgJson = readJSON(text: lbCfg.content)
  String consulapiaddr = lbCfgJson.properties.privateIPAddress
  log.info("Consul LB IP: ${consulapiaddr}")

  // Build json payload for aks service record
  scmjson = JsonOutput.toJson(
    ["Name": serviceName,
     "Service": serviceName,
     "Address": "${serviceIP}",
     "Port": 80
    ])
  log.info("Registering to consul with following record: $scmjson")

  def reqScm = httpRequest(
    httpMode: 'POST',
    acceptType: 'APPLICATION_JSON',
    contentType: 'APPLICATION_JSON',
    url: "http://${consulapiaddr}:8500/v1/agent/service/register",
    requestBody: "${scmjson}",
    consoleLogResponseBody: true,
    validResponseCodes: '200'
  )

}

