package uk.gov.hmcts.contino

class PactBroker {

  static String PACT_BROKER_IMAGE = "hmcts/pact-broker-cli"

  def steps
  def product
  def component
  def brokerUrl

  PactBroker(steps, product, component, brokerUrl) {
    this.steps = steps
    this.product = product
    this.component = component
    this.brokerUrl = brokerUrl
  }

  def canIDeploy(version) {
    steps.withDocker(PACT_BROKER_IMAGE, '--entrypoint ""') {
      steps.sh(
        script: "pact-broker can-i-deploy --retry-while-unknown=12 --retry-interval=10 -a ${this.product}_${this.component} -b ${this.brokerUrl} -e ${version}",
        returnStdout: true
      )?.trim()
    }
  }
}
