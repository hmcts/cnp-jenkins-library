package uk.gov.hmcts.contino


class Helm {

  def steps
  def helm = { cmd, name, options -> return this.steps.sh(script: "helm $cmd $name $options", returnStdout: true)}

  Helm(steps) {
    this.steps = steps
  }

  def setup() {
    init()
    configureAcr()
    addRepo()
  }

  def init() {
    this.steps.sh(returnStatus: true, script: "helm init --client-only")
  }

  def configureAcr() {
    def subscription = this.steps.env.SUBSCRIPTION_NAME
    def acr = (subscription == "sandbox" ? "hmctssandbox" : "hmcts")
    this.steps.sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-${subscription} az configure --defaults acr=${acr}", returnStdout: true)
  }

  def addRepo() {
    def subscription = this.steps.env.SUBSCRIPTION_NAME
    def subscriptionId = this.steps.env.AZURE_SUBSCRIPTION_ID
    this.steps.sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-${subscription} az acr helm repo add  --subscription ${subscriptionId}", returnStdout: true)
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
    def allOptions = ["--install"] + (options == null ? [] : options)
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
