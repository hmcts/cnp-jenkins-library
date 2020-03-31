package uk.gov.hmcts.contino

import groovy.text.SimpleTemplateEngine

class EnvironmentDnsConfig {

  class Entry {
    def environment
    def subscription
    def resourceGroup
    def zone
    def ttl
    def active
    def consulActive
  }

  static final String GITHUB_CREDENTIAL = 'jenkins-github-hmcts-api-token'

  def steps
  static def envDnsConfigMap

  EnvironmentDnsConfig(steps) {
    this.steps = steps
  }

  def getDnsConfig() {
    if (this.envDnsConfigMap == null ){
      def dnsConfigMap = [:]
      def response = steps.httpRequest(
        consoleLogResponseBody: true,
        authentication: "${GITHUB_CREDENTIAL}",
        timeout: 10,
        url: "https://raw.githubusercontent.com/hmcts/cnp-jenkins-config/master/private-dns-config.yml",
        validResponseCodes: '200'
      )
      dnsConfigMap = steps.readYaml (text: response.content)
      this.envDnsConfigMap = mapToEnvironmentConfig(dnsConfigMap)
    }
    return this.envDnsConfigMap
  }

  def mapToEnvironmentConfig(dnsConfigMap) {
    def envDnsConfigMap = [:]
    def engine = new SimpleTemplateEngine()
    for (s in dnsConfigMap['subscriptions']) {
      for (e in s['environments']) {
        def envConfig = new EnvironmentDnsConfig.Entry(
          environment: e['name'],
          subscription: s['name'],
          resourceGroup: s['resourceGroup'],
          ttl: e['ttl'] != null ? e['ttl'] : s['ttl'],
          zone: e['zone'] != null ? e['zone'] : engine.createTemplate(s['zoneTemplate']).make([environment:e['name']]).toString(),
          active: e['active'] != null ? e['active'] : s['active'],
          consulActive: e['consulActive'] != null ? e['consulActive'] : s['consulActive'],
        )
        envDnsConfigMap[e['name']] = envConfig
      }
    }
    return envDnsConfigMap
  }

  def getEntry(environment) {
    return getDnsConfig()[environment]
  }

}
