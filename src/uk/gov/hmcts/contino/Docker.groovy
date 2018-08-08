package uk.gov.hmcts.contino

import uk.gov.hmcts.contino.azure.Acr

class Docker {

  def steps
  def acr

  Docker(steps, Acr acr) {
    this.steps = steps
    this.acr = acr
  }

  def login() {
    this.acr.login()
  }

  def build(DockerImage dockerImage) {
    this.steps.sh(returnStdout: true, script: "docker build -t ${dockerImage.getTaggedName()} .")
  }

  def push(DockerImage dockerImage) {
    this.steps.sh(script: "docker push ${dockerImage.getTaggedName()}", returnStdout: true)
  }

}
