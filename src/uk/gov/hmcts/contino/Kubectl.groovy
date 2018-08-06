package uk.gov.hmcts.contino

import groovy.json.JsonSlurper

class Kubectl {

  // TODO where/what will these be configured
  static String AKS_RESOURCE_GROUP = 'cnp-aks-rg'
  static String AKS_CLUSTER_NAME   = 'cnp-aks-cluster'

  def steps
  def subscription
  def namespace
  def kubectl = { cmd, namespace, returnJsonOutput -> return this.steps.sh(script: "kubectl $cmd $namespace $returnJsonOutput", returnStdout: true)}

  Kubectl(steps, subscription, namespace) {
    this.steps = steps
    this.subscription = subscription
    this.namespace = namespace
  }

  // ignore return status so doesn't fail if namespace already exists
  def createNamespace(String name) {
    this.steps.sh(returnStatus: true, script: "kubectl create namespace ${name}")
  }

  def apply(String path) {
    execute("apply -f ${path}", false)
  }

  def delete(String path) {
    execute("delete -f ${path}", false)
  }

  def getServiceLoadbalancerIP(String name) {
    int maxAttempts = 30
    int attemptCount = 1
    int sleepDuration = 10

    while (true) {
      if (attemptCount == maxAttempts) {
        throw new RuntimeException("Loadbalancer for service ${name} is unavailable.")
      }

      this.steps.echo "Attempt number: ${attemptCount}"
      def ip = getILBIP(name)

      if (ip) {
        return ip
      }

      ++attemptCount
      this.steps.sleep sleepDuration
    }
  }

  def getService(String name) {
    execute("get service ${name}", true)
  }

  // Annoyingly this can't be done in the constructor (constructors only @NonCPS)
  def login() {
    this.steps.sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-${this.subscription} az aks get-credentials --resource-group ${AKS_RESOURCE_GROUP} --name ${AKS_CLUSTER_NAME}", returnStdout: true)
  }

  private String getILBIP(String serviceName) {
    def serviceJson = this.getService(serviceName)
    this.steps.echo "Service Json: ${serviceJson}"
    def serviceObject = new JsonSlurper().parseText(serviceJson)

    // Check if the ILB is still initialising...
    if (!serviceObject.status.loadBalancer.ingress) {
      this.steps.echo "ILB not found or still initialising..."
      return null
    }

    return serviceObject.status.loadBalancer.ingress[0].ip
  }

  private Object execute(String command, boolean returnJsonOutput) {
    kubectl command,"-n ${this.namespace}",
      returnJsonOutput ? '-o json' : ""
  }

}
