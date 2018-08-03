package uk.gov.hmcts.contino

import groovy.json.JsonSlurper

class Kubectl {

  // TODO where/what will these be configured
  static String AKS_RESOURCE_GROUP = 'cnp-aks-rg'
  static String AKS_CLUSTER_NAME   = 'cnp-aks-cluster'

  static String ILB_PENDING = "<pending>"

  def steps
  def subscription
  def namespace
  def kubectl = { cmd, namespace, jsonOutput -> return this.steps.sh(script: "kubectl $cmd $namespace $jsonOutput", returnStdout: true)}

  Kubectl(steps, subscription, namespace) {
    this.steps = steps
    this.subscription = subscription
    this.namespace = namespace
  }

  def apply(String path) {
    execute("apply -f ${path}", false)
  }

  def delete(String path) {
    execute("delete -f ${path}", false)
  }

  def getServiceLoadbalancerIP(String name) {
    int maxRetries = 5
    int retryCount = 0
    int sleepDuration = 5
    def ip

    while (((ip = getILBIP(name)).equals(ILB_PENDING)) && (retryCount < maxRetries)) {
      this.steps.echo "ILB address: ${ip}"
      ++retryCount
      this.steps.echo "Retry count: ${retryCount}"

      if (retryCount == maxRetries) {
        throw new RuntimeException("Loadbalancer for service ${name} is unavailable.")
      }

      this.steps.sleep sleepDuration
    }

    return ip
  }

  def getService(String name, boolean jsonOutput) {
    execute("get service ${name}", jsonOutput)
  }

  // Annoyingly this can't be done in the constructor (constructors only @NonCPS)
  def login() {
    this.steps.sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-${this.subscription} az aks get-credentials --resource-group ${AKS_RESOURCE_GROUP} --name ${AKS_CLUSTER_NAME}", returnStdout: true)
  }

  private String getILBIP(String serviceName) {
    def serviceJson = this.getService(serviceName, true)
    this.steps.echo "Service Json: ${serviceJson}"
    def serviceObject = new JsonSlurper().parseText(serviceJson)

    // Check if the ILB is still initialising...
    if (!serviceObject.status.loadBalancer.ingress) {
      this.steps.echo "ILB not found or still initialising..."
      return ILB_PENDING
    }

    return serviceObject.status.loadBalancer.ingress[0].ip
  }

  private Object execute(String command, boolean jsonOutput) {
    kubectl command,"-n ${this.namespace}",
      jsonOutput ? '-o json' : ""
  }

}
