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

  def installOrUpgradeMulti(List<String> names, List<String> values, List<String> options) {
    def allOptions = ["--install"] + (options == null ? [] : options)
    if (names.size() != values.size()) {
      throw new RuntimeException("Helm charts size (${names.size()}) != values size (${values.size()}).")
    }
    // zip chart name + values files and execute
    [names, values].transpose().each { nv ->
      this.execute("upgrade", nv[0], nv[1..-1], allOptions)
    }
  }

  def installOrUpgrade(String name, List<String> values, List<String> options) {
    def allOptions = ["--install"] + (options == null ? [] : options)
    this.execute("upgrade", name, values, allOptions)
  }

  private Object execute(String command, String name, List<String> values) {
    this.execute(command, name, values, null)
  }

  private Object execute(String command, String name, List<String> values, List<String> options) {
    if (!values) {
      throw new RuntimeException("Helm charts need at least a values file (none given).")
    }
    def optionsStr = "${options == null ?  '' : options.join(' ')}"
    def valuesStr = "${' -f ' + options.join(' -f ')}"
    helm command, name, "${optionsStr} ${valuesStr}"
  }

}
