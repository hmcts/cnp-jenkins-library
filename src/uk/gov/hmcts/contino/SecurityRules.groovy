package uk.gov.hmcts.contino

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

class SecurityRules {

  def steps

  SecurityRules(steps) {
    this.steps = steps
  }

  String getSecurityRules() {
    return "this is a test"
  }
}
