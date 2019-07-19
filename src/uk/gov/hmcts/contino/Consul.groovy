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

    def consulIP = getConsulIP()
    def serviceIPs = getServiceIPs(consulIP, serviceName)
    def validServiceIPs = [:]
    serviceIPs.each { saIP ->
      if (saIP.value != serviceIP) {
        this.steps.log.info("Deregistering IP ${saIP.value} for ${serviceName} from agent: ${saIP.key}.")
        deregisterDns(saIP.key, serviceName)
      } else {
        validServiceIPs[saIP.key] = saIP.value
      }
    }
    if (validServiceIPs) {
      this.steps.log.info("IP ${serviceIP} for ${serviceName} is already registered on agents: ${validServiceIPs.keySet()}.")
      return
    }

    // Build json payload for aks service record
    def json = JsonOutput.toJson(
      ["ID"     : serviceName,
       "Name"   : serviceName,
       "Service": serviceName,
       "Address": "${serviceIP}",
       "Port"   : 80
      ])
    this.steps.log.info("Registering to consul with following record: $json")

    def req = this.steps.httpRequest(
      httpMode: 'PUT',
      acceptType: 'APPLICATION_JSON',
      contentType: 'APPLICATION_JSON',
      url: "http://${consulIP}:8500/v1/agent/service/register",
      requestBody: "${json}",
      consoleLogResponseBody: true,
      validResponseCodes: '200'
    )
  }

  def getServiceIPs(String consulIP, String serviceName) {
    def agentsIP = getServiceAgents(consulIP, serviceName)
    if (!agentsIP) {
      this.steps.log.info("No current registration found for service: $serviceName")
      return [:]
    }
    def agentServiceIps = [:]
    agentsIP.each { agentIP ->
      this.steps.log.info("Getting consul ips for service: $serviceName from agent: $agentIP")
      def res = this.steps.httpRequest(
        httpMode: 'GET',
        acceptType: 'APPLICATION_JSON',
        url: "http://${agentIP}:8500/v1/agent/services",
        consoleLogResponseBody: true,
        validResponseCodes: '100:599',
        quiet: true
      )
      if (res && res.content) {
        def content = new JsonSlurperClassic().parseText(res.content)
        def ip = content.find { rec -> rec.key == serviceName } .value.Address
        agentServiceIps[agentIP] = ip
      }
    }
    this.steps.log.info("Got consul agents and ips for service ${serviceName}: ${agentServiceIps}")
    return agentServiceIps
  }

  def getServiceAgents(String consulIP, String serviceName) {
    if (!serviceName) {
      this.steps.log.info("Invalid service name.")
      return
    }
    this.steps.log.info("Getting consul nodes holding record for service: $serviceName")
    def res = this.steps.httpRequest(
      httpMode: 'GET',
      acceptType: 'APPLICATION_JSON',
      url: "http://${consulIP}:8500/v1/catalog/service/${serviceName}",
      consoleLogResponseBody: true,
      validResponseCodes: '200'
    )
    if (!res || !res.content) {
      return []
    }
    return new JsonSlurperClassic().parseText(res.content).Address
  }

  def deregisterDns(String nodeIP, String serviceName) {
    if (!serviceName) {
      return
    }
    this.steps.log.info("Deregistering from consul record for service: $serviceName")
    this.steps.httpRequest(
      httpMode: 'PUT',
      acceptType: 'APPLICATION_JSON',
      url: "http://${nodeIP}:8500/v1/agent/service/deregister/${serviceName}",
      consoleLogResponseBody: true,
      validResponseCodes: '200'
    )
  }

}
