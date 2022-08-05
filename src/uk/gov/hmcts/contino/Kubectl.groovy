package uk.gov.hmcts.contino

import groovy.json.JsonSlurper
import uk.gov.hmcts.pipeline.AKSSubscriptions

class Kubectl {

  def steps
  def subscription
  def aksSubscription
  def namespace
  def resourceGroup
  def clusterName
  def kubectl = { cmd, namespace, returnJsonOutput -> return this.steps.sh(script: "kubectl $cmd $namespace $returnJsonOutput", returnStdout: true)}

  Kubectl(steps, subscription, namespace) {
    this.steps = steps
    this.subscription = subscription
    this.namespace = namespace
    this.resourceGroup = steps.env.AKS_RESOURCE_GROUP
    this.clusterName = steps.env.AKS_CLUSTER_NAME
    this.aksSubscription = new AKSSubscriptions(steps).preview.name
  }

  Kubectl(steps, subscription, namespace, aksSubscription) {
    this.steps = steps
    this.subscription = subscription
    this.namespace = namespace
    this.resourceGroup = steps.env.AKS_RESOURCE_GROUP
    this.clusterName = steps.env.AKS_CLUSTER_NAME
    this.aksSubscription = aksSubscription
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

  def deleteDeployment(String deploymentName) {
    this.steps.sh(returnStatus: true, script: "kubectl delete deployment ${deploymentName} -n ${this.namespace}")
  }

  def getJob(String name) {
    this.steps.sh(returnStatus: true, script: "kubectl get job ${name} -n ${this.namespace}")
  }

  def deleteJob(String name) {
    this.steps.sh(returnStatus: true, script: "kubectl delete job ${name} -n ${this.namespace}")
  }

  def getServiceLoadbalancerIP(String name) {
    this.getServiceLoadbalancerIP(name, this.namespace)
  }

  def getServiceLoadbalancerIP(String name, String namespace) {
    int maxAttempts = 30
    int attemptCount = 1
    int sleepDuration = 10

    while (true) {
      if (attemptCount == maxAttempts) {
        throw new RuntimeException("Loadbalancer for service ${name} is unavailable.")
      }

      this.steps.echo "Attempt number: ${attemptCount}"
      def ip = getILBIP(name, namespace)

      if (ip) {
        return ip
      }

      ++attemptCount
      this.steps.sleep sleepDuration
    }
  }

  def getService(String name) {
    this.getService(name, this.namespace)
  }

  def getService(String name, String namespace) {
    this.execute("get service ${name}", namespace, true)
  }

  // Annoyingly this can't be done in the constructor (constructors only @NonCPS)
  def login() {
      this.steps.sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-${this.subscription} az aks get-credentials --resource-group ${this.resourceGroup} --name ${this.clusterName} --subscription  ${aksSubscription} -a ", returnStdout: true)
  }

  private String getILBIP(String serviceName, String namespace) {
    def serviceJson = this.getService(serviceName, namespace)
    this.steps.echo "Service Json: ${serviceJson}"
    def serviceObject = new JsonSlurper().parseText(serviceJson)

    if (!serviceObject.status.loadBalancer.ingress) {
      this.steps.echo "ILB not found or still initialising..."
      return null
    }

    return serviceObject.status.loadBalancer.ingress[0].ip
  }

  def getSecret(String name, String namespace, String jsonPath) {
    def secretBase64 = this.executeAndExtract("get secret ${name}", namespace, jsonPath)
    return secretBase64 == null ? null : new String(secretBase64.decodeBase64())
  }

  private Object execute(String command, boolean returnJsonOutput) {
    this.execute(command, this.namespace, returnJsonOutput)
  }

  private Object execute(String command, String namespace, boolean returnJsonOutput) {
    kubectl command,"-n ${namespace}",
      returnJsonOutput ? '-o json' : ""
  }

  private Object executeAndExtract(String command, String namespace, String jsonPath) {
    kubectl command,"-n ${namespace}", "-o json -o=jsonpath=" + jsonPath
  }

}
