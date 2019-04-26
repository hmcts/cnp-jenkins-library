package uk.gov.hmcts.contino

import com.cloudbees.groovy.cps.NonCPS

abstract class AbstractBuilder implements Builder, Serializable {

  def steps
  def gatling
  def securitytest

  AbstractBuilder(steps) {
    this.steps = steps
    this.gatling = new Gatling(this.steps)
    this.securitytest = new SecurityScan(this.steps)
  }

  @Override
  @NonCPS
  def performanceTest() {
    this.gatling.execute()
  }

  @Override
  def securityScan(){
    this.securitytest.execute()
  }
}
