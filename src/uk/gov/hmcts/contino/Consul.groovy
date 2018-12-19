package uk.gov.hmcts.contino

import groovy.json.JsonOutput

import uk.gov.hmcts.contino.azure.Az

class Consul {

  def steps
  def subscription
  def az
  private consulApiAddr

  Consul(steps) {
    this.steps = steps
    this.subscription = this.steps.env.SUBSCRIPTION_NAME
    this.az = new Az(this.steps, this.subscription)
  }

  def getConsulIP() {
    if (this.consulApiAddr) {
      return this.consulApiAddr
    }
    this.steps.log.info "Getting consul's IP address ..."
    
    def subscriptionResourceGroupMap = [
        'nonprod'  : 'core-infra-preview',
        'hmctsdemo'  : 'core-infra-hmctsdemo'
    ]

    def consulResourceGroup = subscriptionResourceGroupMap.subscription ?: 'core-infra-saat'

    def tempConsulIpAddr = this.az.az "network lb frontend-ip show  -g ${consulResourceGroup} --lb-name consul-server_dns --name PrivateIPAddress --query privateIpAddress -o tsv"
    this.consulApiAddr = tempConsulIpAddr?.trim()
    if (this.consulApiAddr == null || "".equals(this.consulApiAddr)) {
      throw new RuntimeException("Failed to retrieve Consul LB IP")
    }

    this.steps.log.info("Consul LB IP: ${this.consulApiAddr}")
    return this.consulApiAddr
  }

  def registerDns(serviceName, serviceIP) {
    // Build json payload for aks service record
    def json = JsonOutput.toJson(
      ["Name": serviceName,
      "Service": serviceName,
      "Address": "${serviceIP}",
      "Port": 80
      ])
    this.steps.log.info("Registering to consul with following record: $json")

    def req = this.steps.httpRequest(
      httpMode: 'PUT',
      acceptType: 'APPLICATION_JSON',
      contentType: 'APPLICATION_JSON',
      url: "http://${getConsulIP()}:8500/v1/agent/service/register",
      requestBody: "${json}",
      consoleLogResponseBody: true,
      validResponseCodes: '200'
    )
  }

}
