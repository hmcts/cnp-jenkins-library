package uk.gov.hmcts.contino

import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
import uk.gov.hmcts.contino.azure.Az


class Consul {

  def steps
  def subscription
  def environment
  def az
  private consulApiAddr

  Consul(steps, environment) {
    this.steps = steps
    this.subscription = this.steps.env.SUBSCRIPTION_NAME
    this.environment = environment
    this.az = new Az(this.steps, this.subscription)
  }

  Consul(steps) {
    Consul (steps, new Environment(steps).nonProdName)
  }

  def getConsulIP() {
    if (this.consulApiAddr) {
      return this.consulApiAddr
    }
    this.steps.log.info "Getting consul's IP address ..."

    def tempConsulIpAddr = this.az.az "network lb frontend-ip show  -g core-infra-${environment}  --lb-name consul-server_dns --name PrivateIPAddress --query privateIpAddress -o tsv"
    this.consulApiAddr = tempConsulIpAddr?.trim()
    if (this.consulApiAddr == null || "".equals(this.consulApiAddr)) {
      throw new RuntimeException("Failed to retrieve Consul LB IP")
    }

    this.steps.log.info("Consul LB IP: ${this.consulApiAddr}")
    return this.consulApiAddr
  }

  def registerDns(serviceName, serviceIP) {
    if (!IPV4Validator.validate(serviceIP)) {
      throw new RuntimeException("Invalid IP address [${serviceIP}].")
    }

    def addresses = getIpAddresses(serviceName)
    if (addresses.contains(serviceIP) && addresses.size() == 1) {
      return   // service is already registered, nothing to do
    }
    deregisterDns(serviceName)

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

  def getDnsRecord(String serviceName) {
    this.steps.log.info("Getting consul record for service: $serviceName")
    def res = this.steps.httpRequest(
      httpMode: 'GET',
      acceptType: 'APPLICATION_JSON',
      url: "http://${getConsulIP()}:8500/v1/agent/service/${serviceName}",
      consoleLogResponseBody: true,
      validResponseCodes: '100:599'
    )
    this.steps.log.info("Got consul record: $res")
  }

  def getIpAddresses(String serviceName) {
    this.steps.log.info("Getting ip address(es) for service: $serviceName")
    def res = getDnsRecord(serviceName)
    if (!res) {
      return []
    }
    def taggedAddresses = new JsonSlurperClassic().parseText(res.content).taggedAddresses
    if (!taggedAddresses) {
      return []
    }
    return taggedAddresses.each { ta -> ta.address }
  }

  def deregisterDns(String serviceName) {
    if (!serviceName) {
      return
    }
    this.steps.log.info("Deregistering from consul record for service: $serviceName")
    this.steps.httpRequest(
      httpMode: 'PUT',
      acceptType: 'APPLICATION_JSON',
      url: "http://${getConsulIP()}:8500/v1/agent/service/deregister/${serviceName}",
      consoleLogResponseBody: true,
      validResponseCodes: '200'
    )
  }

}
