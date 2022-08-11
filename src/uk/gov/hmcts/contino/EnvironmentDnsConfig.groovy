package uk.gov.hmcts.contino

import groovy.text.SimpleTemplateEngine

class EnvironmentDnsConfig {

  def steps
  static def envDnsConfigMap

  EnvironmentDnsConfig(steps) {
    this.steps = steps
  }

  def getDnsConfig(product) {
    if (this.envDnsConfigMap == null ){
      def repo = steps.env.JENKINS_CONFIG_REPO ?: "cnp-jenkins-config"

      def dnsConfigMap = [:]
      def response = steps.httpRequest(
        consoleLogResponseBody: true,
        authentication: steps.env.GIT_CREDENTIALS_ID,
        timeout: 10,
        url: "https://raw.githubusercontent.com/hmcts/${repo}/master/private-dns-config.yml",
        validResponseCodes: '200'
      )
      dnsConfigMap = steps.readYaml (text: response.content)
      this.envDnsConfigMap = mapToEnvironmentConfig(product, dnsConfigMap)
    }
    return this.envDnsConfigMap
  }

  def mapToEnvironmentConfig(product, dnsConfigMap) {
    def envDnsConfigMap = [:]
    def engine = new SimpleTemplateEngine()
    for (s in dnsConfigMap['subscriptions']) {
      for (e in s['environments']) {
        def envConfig = new EnvironmentDnsConfigEntry(
          environment: e['name'],
          subscription: s['name'],
          resourceGroup: s['resourceGroup'],
          ttl: e['ttl'] != null ? e['ttl'] : s['ttl'],
          zone: e['zone'] != null ? e['zone'] : engine.createTemplate(s['zoneTemplate']).make([environment:e['name'], product: product]).toString(),
          active: e['active'] != null ? e['active'] : s['active'],
        )
        envDnsConfigMap[e['name']] = envConfig
      }
    }
    return envDnsConfigMap
  }

  def getEntry(environment, product) {
    return getDnsConfig(product)[environment]
  }

}
