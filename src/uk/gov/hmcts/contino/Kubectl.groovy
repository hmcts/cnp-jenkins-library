package uk.gov.hmcts.contino

class Kubectl {

  // TODO where/what will these be configured
  static String AKS_RESOURCE_GROUP = 'cnp-aks-rg'
  static String AKS_CLUSTER_NAME   = 'cnp-aks-cluster'

  def steps
  def namespace

  def kubectl = { cmd, namespace, jsonOutput -> return this.steps.sh(script: "kubectl $cmd $namespace $jsonOutput", returnStdout: true)}

  Kubectl(steps, subscription, namespace) {
    this.steps = steps
    this.namespace = namespace
    this.login(subscription)
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

  private void login(String subscription) {
    def az = { cmd -> return this.steps.sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$subscription az $cmd", returnStdout: true)}
    //az "aks get-credentials --resource-group ${AKS_RESOURCE_GROUP} --name ${AKS_CLUSTER_NAME}"
    az "aks get-credentials --resource-group cnp-aks-rg --name cnp-aks-cluster"
  }

  private Object execute(String command, boolean jsonOutput) {
    kubectl command,"-n ${this.namespace}",
      jsonOutput ? '-o json' : ""
  }

}
