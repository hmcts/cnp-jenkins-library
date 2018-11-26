package uk.gov.hmcts.contino


class Helm {

  def steps
  def helm = { cmd, name, options -> return this.steps.sh(script: "helm $cmd $options $name", returnStdout: true)}

  Helm(steps) {
    this.steps = steps
  }

  def init() {
    this.steps.sh(returnStatus: true, script: "helm init --client-only")
    def subscription = this.steps.env.SUBSCRIPTION_NAME
    def subscriptionId = steps.env.AZURE_SUBSCRIPTION_ID
    def acr = (subscription == "sandbox" ? "hmctssandbox" : "hmcts")
    this.steps.sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-${subscription} az configure --defaults acr=${acr}", returnStdout: true)
    this.steps.sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-${subscription} az acr helm repo add  --subscription ${subscriptionId}", returnStdout: true)
    // debug log
    /*
    this.steps.echo "subscription: ${subscription} - subscriptionId: ${subscriptionId.substring(0, subscriptionId.length() -1)} - acr: ${acr.substring(0, acr.length() -1)}"
    def repos = this.steps.sh(script: "helm repo list", returnStdout: true)
    this.steps.echo "${repos.replaceAll("hmctssandbox", "hmctssandbo")}"
    def search = this.steps.sh(script: "helm search hmctssandbox", returnStdout: true)
    this.steps.echo "${search.replaceAll("hmctssandbox", "hmctssandbo")}"
    def list = this.steps.sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-${subscription} az acr helm list --name ${acr}", returnStdout: true)
    this.steps.echo "${list.replaceAll("hmctssandbox", "hmctssandbo")}"
    */
  }

  def installOrUpgradeMulti(String path, List<String> names, List<String> values) {
    this.installOrUpgradeMulti(names, values, null)
  }

  def installOrUpgradeMulti(String path, List<String> names, List<String> values, List<String> options) {
    // zip chart name + related values files and execute
    [names, values].transpose().each { nv ->
      this.installOrUpgrade(path, nv[0], nv[1..-1].flatten(), options)
    }
  }

  def installOrUpgrade(String path, String name, List<String> values, List<String> options) {
    if (!values) {
      throw new RuntimeException("Helm charts need at least a values file (none given).")
    }

    def valuesPrint = this.steps.sh(script: "cat ${values[0]}", returnStdout: true)
    this.steps.echo "${valuesPrint.replaceAll("hmctssandbox", "hmctssandbo")}"

    this.dependencyUpdate("${path}/${name}")
    def allOptions = ["--install", "--force"] + (options == null ? [] : options)
    this.execute("upgrade", "${name} ${path}/${name}", values, allOptions)
  }

  def dependencyUpdate(String path) {
    this.execute("dependency update", path, null)
  }

  def delete(String name) {
    this.execute("delete", name, ["--purge"])
  }

  private Object execute(String command, String name, List<String> values) {
    this.execute(command, name, values, null)
  }

  private Object execute(String command, String name, List<String> values, List<String> options) {
    def optionsStr = "${options == null ?  '' : options.join(' ')}"
    def valuesStr = (values == null ? "" : "${' -f ' + values.join(' -f ')}")
    helm command, name, "${valuesStr} ${optionsStr}"
  }

}
