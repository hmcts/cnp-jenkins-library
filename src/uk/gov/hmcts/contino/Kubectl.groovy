package uk.gov.hmcts.contino

import groovy.json.JsonSlurper

class Kubectl {

  // TODO where/what will these be configured
  static String AKS_RESOURCE_GROUP = 'cnp-aks-rg'
  static String AKS_CLUSTER_NAME   = 'cnp-aks-cluster'

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
    int sleepDuration = 10
    def ip

    while ((ip = getILBIP(name)) == '<pending>' && (retryCount < maxRetries)) {
      ++retryCount
      println "Retry count: ${retryCount}"

      if (retryCount == maxRetries) {
        throw new RuntimeException("Loadbalancer unavailable.")
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

  private getILBIP(String serviceName) {
    def serviceJson = this.getService(serviceName, true)
    this.steps.echo "Service Json: ${serviceJson}"

    def serviceObject = new JsonSlurper().parseText(serviceJson)
    return serviceObject.status.loadBalancer.ingress[0].ip
  }

  private execute(String command, boolean jsonOutput) {
    kubectl command,"-n ${this.namespace}",
      jsonOutput ? '-o json' : ""
  }

}
