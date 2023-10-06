package uk.gov.hmcts.contino

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

class SecurityRules {

  def steps

  SecurityRules(steps) {
    this.steps = steps
  }

  String getSecurityRules() {
    return steps.httpRequest(httpMode: 'GET',
      acceptType: 'APPLICATION_JSON',
      url: "https://raw.githubusercontent.com/hmcts/security-test-rules/master/conf/security-rules.conf")
  }
}
