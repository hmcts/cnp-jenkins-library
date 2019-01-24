package uk.gov.hmcts.contino

import uk.gov.hmcts.contino.azure.Acr


class Helm {

  def steps
  def acr
  def helm = { cmd, name, options -> return this.steps.sh(script: "helm $cmd $name $options", returnStdout: true)}

  def subscription
  def subscriptionId
  def resourceGroup
  def registryName
  
  def subscriptionToDockerRegistryMapping = [
    'sandbox' : 'hmctssandbox',
    'hmctsdemo' : 'hmctsdemo'
  ]

  Helm(steps) {
    this.steps = steps
    this.subscription = this.steps.env.SUBSCRIPTION_NAME
    this.subscriptionId = this.steps.env.AZURE_SUBSCRIPTION_ID
    this.resourceGroup = this.steps.env.AKS_RESOURCE_GROUP
    this.registryName = (registryNameSubscriptionMap.subscription ?: "hmcts")
    this.acr = new Acr(this.steps, subscription, registryName, resourceGroup)
  }

  def setup() {
    init()
    configureAcr()
    addRepo()
  }

  def init() {
    this.helm "init", "", "--client-only"
  }

  def configureAcr() {
    this.acr.az "configure --defaults acr=${registryName}"
  }

  def addRepo() {
    this.acr.az "acr helm repo add --subscription ${subscriptionId}"
  }

  def installOrUpgradeMulti(String path, List<String> names, List<String> values) {
    this.installOrUpgradeMulti(path, names, values, null)
  }

  def installOrUpgradeMulti(String path, List<String> names, List<String> values, List<String> options) {
    // zip chart name + related values files and execute
    [names, values].transpose().each { nv ->
      this.installOrUpgrade(path, nv[0], nv[1..-1], options)
    }
  }

  def installOrUpgrade(String path, String name, List<String> values, List<String> options) {
    if (!values) {
      throw new RuntimeException("Helm charts need at least a values file (none given).")
    }
    this.dependencyUpdate(path)
    def allOptions = ["--install", "--wait"] + (options == null ? [] : options)
    def allValues = values.flatten()
    this.execute("upgrade", "${name} ${path}", allValues, allOptions)
  }

  def dependencyUpdate(String path) {
    this.execute("dependency update", path)
  }

  def delete(String name) {
    this.execute("delete", name, null, ["--purge"])
  }

  private Object execute(String command, String name) {
    this.execute(command, name, null, null)
  }

  private Object execute(String command, String name, List<String> values, List<String> options) {
    def optionsStr = "${options == null ?  '' : options.join(' ')}"
    def valuesStr = (values == null ? "" : "${' -f ' + values.join(' -f ')}")
    helm command, name, "${valuesStr} ${optionsStr}"
  }

}
