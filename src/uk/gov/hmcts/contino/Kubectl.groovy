package uk.gov.hmcts.contino

class Kubectl {

  def steps
  def namespace

  def kubectl = { cmd, namespace, jsonOutput -> return this.steps.sh(script: "kubectl $cmd $namespace $jsonOutput", returnStdout: true)}

  Kubectl(steps, namespace) {
    this.steps = steps
    this.namespace = namespace
  }

  def apply(String path) {
    execute("apply -f ${path}", false)
  }

  def delete(String path) {
    execute("delete -f ${path}", false)
  }

  def getService(String name) {
    execute("get service ${name}", true)
  }

  def execute(String command, boolean jsonOutput) {
    kubectl command,"-n ${this.namespace}",
      jsonOutput ? '-o json' : ""
  }

}
