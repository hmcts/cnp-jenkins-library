package uk.gov.hmcts.contino

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

class SecurityRules {

  def steps

  SecurityRules(steps) {
    this.steps = steps
  }

  String getSecurityRules() {
    def response = steps.httpRequest url: "https://raw.githubusercontent.com/hmcts/security-test-rules/master/conf/security-rules.conf", httpMode: 'GET', acceptType: 'APPLICATION_JSON'
  }
}
