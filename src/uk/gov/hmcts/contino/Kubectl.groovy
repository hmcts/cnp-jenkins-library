package uk.gov.hmcts.contino

class Kubectl {

  // TODO where/what will these be configured
  static String AKS_RESOURCE_GROUP = 'cnp-aks-rg'
  static String AKS_CLUSTER_NAME   = 'cnp-aks-cluster'

  def steps
  def namespace
  def kubectl = { cmd, namespace, jsonOutput -> return this.steps.sh(script: "kubectl $cmd $namespace $jsonOutput", returnStdout: true)}
  def az = { sub, cmd -> return this.steps.sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$sub az $cmd", returnStdout: true)}

  Kubectl(steps, subscription, namespace) {
    this.steps = steps
    this.namespace = namespace

    this.steps.echo "Logging in with ${subscription}"
    this.steps.sh "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$subscription az aks get-credentials --resource-group cnp-aks-rg --name cnp-aks-cluster"
    //az subscription, "aks get-credentials --resource-group cnp-aks-rg --name cnp-aks-cluster"
    //this.login(subscription)
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

//  private void login(String subscription) {
//    this.steps.echo "Logging in with ${subscription}"
//    az subscription, "aks get-credentials --resource-group cnp-aks-rg --name cnp-aks-cluster"
//  }

  private Object execute(String command, boolean jsonOutput) {
    kubectl command,"-n ${this.namespace}",
      jsonOutput ? '-o json' : ""
  }

}
