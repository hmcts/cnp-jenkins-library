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

  /**
   * Runs the can-i-deploy command
   * @param version the version of the current project, usually a git commit hash
   * @return
   */
  def canIDeploy(version) {
    def reference = "${this.product}_${this.component}"
    steps.withDocker(PACT_BROKER_IMAGE, '--entrypoint ""') {
      steps.sh(
        script: "pact-broker can-i-deploy --retry-while-unknown=12 --retry-interval=10 -a ${reference} -b ${this.brokerUrl} -e ${version}",
        returnStdout: true
      )?.trim()
    }
  }
}
