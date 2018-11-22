package uk.gov.hmcts.contino


class Helm {

  def steps
  def helm = { cmd, name, options -> return this.steps.sh(script: "helm $cmd $options $name", returnStdout: true)}

  Helm(steps) {
    this.steps = steps
    this.steps.sh(returnStatus: true, script: "helm init --client-only")
  }

  def init() {
    this.steps.sh(returnStatus: true, script: "helm init --client-only")
  }

  def installOrUpgradeMulti(List<String> names, List<String> values) {
    this.installOrUpgradeMulti(names, values, null)
  }

  def installOrUpgradeMulti(List<String> names, List<String> values, List<String> options) {
    // zip chart name + related values files and execute
    [names, values].transpose().each { nv ->
      this.installOrUpgrade("upgrade", nv[0], nv[1..-1], options)
    }
  }

  def installOrUpgrade(String name, List<String> values, List<String> options) {
    if (!values) {
      throw new RuntimeException("Helm charts need at least a values file (none given).")
    }
    this.dependencyUpdate(name)
    def allOptions = ["--install"] + (options == null ? [] : options)
    this.execute("upgrade", name, values, allOptions)
  }

  def dependencyUpdate(String name) {
    this.execute("dependency update", name, null)
  }

  private Object execute(String command, String name, List<String> values) {
    this.execute(command, name, values, null)
  }

  private Object execute(String command, String name, List<String> values, List<String> options) {
    def optionsStr = "${options == null ?  '' : options.join(' ')}"
    def valuesStr = "${' -f ' + values.join(' -f ')}"
    helm command, name, "${optionsStr} ${valuesStr}"
  }

}
