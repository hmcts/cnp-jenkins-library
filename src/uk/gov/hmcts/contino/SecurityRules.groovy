package uk.gov.hmcts.contino

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

class SecurityRules {

  def steps
  static def response

  SecurityRules(steps) {
    this.steps = steps
  }

  def getSecurityRules() {
    def response = steps.httpRequest(httpMode: 'GET',
      acceptType: 'APPLICATION_JSON',
      url: "https://raw.githubusercontent.com/hmcts/security-test-rules/master/conf/security-rules.conf",
      validResponseCodes: '200')
    return response
  }
}
