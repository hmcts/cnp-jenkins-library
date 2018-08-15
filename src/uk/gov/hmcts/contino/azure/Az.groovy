package uk.gov.hmcts.contino.azure

abstract class Az {

  def steps
  def subscription
  def az

  Az(steps, subscription) {
    this.steps = steps
    this.subscription = subscription

    this.az = { cmd ->
      return steps.sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-${this.subscription} az ${cmd}", returnStdout: true)?.trim()
    }
  }

}
