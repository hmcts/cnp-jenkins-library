package uk.gov.hmcts.contino

import uk.gov.hmcts.contino.azure.Acr


class Helm {

  public static final String HELM_RESOURCES_DIR = "charts"
  def steps
  def acr
  def helm = { cmd, name, options -> return this.steps.sh(script: "helm $cmd $name $options", returnStdout: true)}

  def subscription
  def subscriptionId
  def resourceGroup
  def registryName
  String chartLocation
  def chartName

  Helm(steps, String chartName) {
    this.steps = steps
    this.subscription = this.steps.env.SUBSCRIPTION_NAME
    this.subscriptionId = this.steps.env.AZURE_SUBSCRIPTION_ID
    this.resourceGroup = this.steps.env.AKS_RESOURCE_GROUP
    this.registryName = (subscription == "sandbox" ? "hmctssandbox" : "hmcts")
    this.acr = new Acr(this.steps, subscription, registryName, resourceGroup)
    this.chartLocation = "${HELM_RESOURCES_DIR}/${chartName}"
    this.chartName = chartName
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

  def publishIfNotExists(List<String> values) {
    configureAcr()
    addRepo()
    dependencyUpdate()
    lint(values)

    def version = this.steps.sh(script: "helm inspect chart ${this.chartLocation}  | grep version | cut -d  ':' -f 2", returnStdout: true).trim()
    this.steps.echo "Version of chart locally is: ${version}"
    def resultOfSearch = this.acr.az "acr helm show --name ${registryName} ${this.chartName} --version ${version} --query version -o tsv"
    this.steps.echo "Searched remote repo ${registryName}, result was ${resultOfSearch}"

    if (resultOfSearch != version) {
      this.steps.echo "Publishing new version of ${this.chartName}"

      this.steps.sh "helm package ${this.chartLocation}"
      this.acr.az "acr helm push --name ${registryName} ${this.chartName}-${version}.tgz"

      this.steps.echo "Published ${this.chartName}-${version} to ${registryName}"
    } else {
      this.steps.echo "Chart already published, skipping publish, bump the version in ${this.chartLocation}/Chart.yaml if you want it to be published"
    }
  }

  def lint(List<String> values) {
    this.execute("lint", this.chartLocation, values, null)
  }

  def installOrUpgrade(String imageTag, List<String> values, List<String> options) {
    if (!values) {
      throw new RuntimeException("Helm charts need at least a values file (none given).")
    }

    dependencyUpdate()
    lint(values)

    def allOptions = ["--install", "--wait", "--timeout 500"] + (options == null ? [] : options)
    def allValues = values.flatten()
    this.execute("upgrade", "${this.chartName}-${imageTag} ${this.chartLocation}", allValues, allOptions)
  }

  def dependencyUpdate() {
    this.execute("dependency update", this.chartLocation)
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
