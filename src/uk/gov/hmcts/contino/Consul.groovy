package uk.gov.hmcts.contino

import groovy.json.JsonOutput

import uk.gov.hmcts.contino.azure.Az

class Consul {

  def steps  
  def subscription
  def authtoken
  def consulapiaddr
  def az

  Consul(steps) {
    this.steps = steps
    this.subscription = this.steps.env.SUBSCRIPTION_NAME
    this.az = new Az(this.steps, this.subscription)
  }

  def getConsulIP() {    
    if (this.consulapiaddr) {
      return this.consulapiaddr
    }
    this.steps.log.info "Getting consul's IP address ..."    
    
    def cip = this.az.az "network lb frontend-ip show  -g core-infra-${subscription  == 'nonprod' ? 'preview' : 'saat'}  --lb-name consul-server_dns --name PrivateIPAddress --query privateIpAddress -o tsv" 
    this.consulapiaddr = cip?.trim()
    if (this.consulapiaddr == null || "".equals(this.consulapiaddr)) {
      throw new RuntimeException("Failed to retrieve Consul LB IP")
    }

    this.steps.log.info("Consul LB IP: ${this.consulapiaddr}")
    this.steps.env.CONSUL_LB_IP = this.consulapiaddr
    return this.consulapiaddr
  }

  def registerConsulDns(serviceName, serviceIP) {
    // Build json payload for aks service record
    getConsulIP()
    def json = JsonOutput.toJson(
      ["Name": serviceName,
      "Service": serviceName,
      "Address": "${serviceIP}",
      "Port": 80
      ])
    this.steps.log.info("Registering to consul with following record: $json")

    def req = this.steps.httpRequest(
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