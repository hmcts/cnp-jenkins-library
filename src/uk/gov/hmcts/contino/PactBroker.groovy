package uk.gov.hmcts.contino

class PactBroker {

  static String PACT_BROKER_URL = "https://pact-broker.platform.hmcts.net"
  static String PACT_BROKER_IMAGE = "hmcts/pact-broker-cli"

  def steps
  def product
  def component

  PactBroker(steps, product, component) {
    this.steps = steps
    this.product = product
    this.component = component
  }

  def canIDeploy(version) {
    steps.withDocker(PACT_BROKER_IMAGE, '--entrypoint ""') {
      steps.sh(
        script: "pact-broker can-i-deploy --retry-while-unknown=12 --retry-interval=10 -a ${this.product}_${this.component} -b ${PACT_BROKER_URL} -e ${version}",
        returnStdout: true
      )?.trim()
    }
  }
}
