package uk.gov.hmcts.contino

class Docker {

  def steps

  Docker(steps) {
    this.steps = steps
  }

  def loginDockerHub(username, password) {
    this.steps.sh(returnStdout: true, script: "docker login -u ${username} -p ${password}")
  }

  def login(hostUrl, username, password) {
    this.steps.sh(returnStdout: true, script: "docker login ${hostUrl} -u ${username} -p ${password}")
  }

  def build(DockerImage dockerImage) {
    this.steps.sh(returnStdout: true, script: "docker build -t ${dockerImage.getTaggedName()} .")
  }

  def push(DockerImage dockerImage) {
    this.steps.sh(script: "docker push ${dockerImage.getTaggedName()}", returnStdout: true)
  }

}
