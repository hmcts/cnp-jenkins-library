package uk.gov.hmcts.contino

import uk.gov.hmcts.contino.azure.Acr


class Consul {

  def steps  
  def subscription
  def authtoken
  def consulapiaddr

  Consul(steps) {
    this.steps = steps
    this.subscription = this.steps.env.SUBSCRIPTION_NAME
  }

  private getAuthToken() {
    if (this.authtoken) {
      return this.authtoken
    }
    def response = this.steps.httpRequest(
        httpMode: 'POST',
        customHeaders: [[name: 'ContentType', value: "application/x-www-form-urlencoded"]],
        requestBody: "grant_type=client_credentials&resource=https%3A%2F%2Fmanagement.azure.com%2F&client_id=${this.steps.env.ARM_CLIENT_ID}&client_secret=${this.steps.env.ARM_CLIENT_SECRET}",
        acceptType: 'APPLICATION_JSON',
        url: "https://login.microsoftonline.com/${this.steps.env.ARM_TENANT_ID}/oauth2/token")
    this.authtoken = this.steps.readJSON(text: response.content).access_token    
    return this.authtoken
  }    

  def getConsulIP() {    
    if (this.consulapiaddr) {
      return this.consulapiaddr
    }
    this.steps.log.info "Getting consul's IP address ..."    
    getAuthToken()
    def lbCfg = this.steps.httpRequest(
      httpMode: 'GET',
      customHeaders: [[name: 'Authorization', value: "Bearer ${authtoken}"]],
      url: "https://management.azure.com/subscriptions/${this.steps.env.ARM_SUBSCRIPTION_ID}/resourceGroups/core-infra-${subscription in ['nonprod', 'prod']?'preview':'saat'}/providers/Microsoft.Network/loadBalancers/consul-server_dns/frontendIPConfigurations/privateIPAddress?api-version=2018-04-01")
    if (! lbCfg.content?.trim()) {
      lthis.steps.og.debug(lbCfg.content)
      error("Something went wrong finding consul lb")
    }
    lbCfgJson = this.steps.readJSON(text: lbCfg.content)
    this.consulapiaddr = lbCfgJson.properties.privateIPAddress
    this.steps.log.info("Consul LB IP: ${this.consulapiaddr}")
    this.steps.env.CONSUL_LB_IP = consulapiaddr
    return this.consulapiaddr
  }

  def registerConsulDns(serviceName, serviceIP) {
    // Build json payload for aks service record
    getConsulIP()
    json = JsonOutput.toJson(
      ["Name": serviceName,
      "Service": serviceName,
      "Address": "${serviceIP}",
      "Port": 80
      ])
    this.steps.log.info("Registering to consul with following record: $json")

    req = this.steps.httpRequest(
      httpMode: 'POST',
      acceptType: 'APPLICATION_JSON',
      contentType: 'APPLICATION_JSON',
      url: "http://${this.consulapiaddr}:8500/v1/agent/service/register",
      requestBody: "${json}",
      consoleLogResponseBody: true,
      validResponseCodes: '200'
    )
  }

}