package uk.gov.hmcts.contino

import uk.gov.hmcts.contino.azure.Acr
import groovy.json.JsonSlurper


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
  def notFoundMessage = "Not found"
  String registrySubscription
  String tlsOptions = ""

  Helm(steps, String chartName) {
    this.steps = steps
    this.subscription = this.steps.env.SUBSCRIPTION_NAME
    this.subscriptionId = this.steps.env.AZURE_SUBSCRIPTION_ID
    this.resourceGroup = this.steps.env.AKS_RESOURCE_GROUP
    this.registryName = this.steps.env.REGISTRY_NAME
    this.registrySubscription = this.steps.env.REGISTRY_SUBSCRIPTION
    this.acr = new Acr(this.steps, subscription, registryName, resourceGroup, registrySubscription)
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
    this.acr.az "acr helm repo add --subscription ${registrySubscription} --name ${registryName}"

    if (this.subscription != 'sandbox') {
      // to be removed when hmcts registry is deleted, kept in to maintain backwards compatibility
      this.acr.az "acr helm repo add --subscription DCD-CNP-DEV --name hmcts"
    }
  }

  def enableTLS(String aksSubscription, String keyVaultName){
    def az = { cmd -> return steps.sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-jenkins az $cmd", returnStdout: true).trim() }
    def helmRoot = (this.helm ("home", "", "") ).trim()
    az "keyvault secret download --vault-name '$keyVaultName' --name 'helm-pki-ca-cert' --subscription '$aksSubscription' --file $helmRoot/ca.pem "
    az "keyvault secret download --vault-name '$keyVaultName' --name 'helm-pki-helm-cert' --subscription '$aksSubscription' --file $helmRoot/cert.pem "
    az "keyvault secret download --vault-name '$keyVaultName' --name 'helm-pki-helm-key' --subscription '$aksSubscription' --file $helmRoot/key.pem "
    this.tlsOptions = "--tls"
  }

  def publishIfNotExists(List<String> values) {
    configureAcr()
    addRepo()
    dependencyUpdate()
    lint(values)

    def version = this.steps.sh(script: "helm inspect chart ${this.chartLocation}  | grep version | cut -d  ':' -f 2", returnStdout: true).trim()
    this.steps.echo "Version of chart locally is: ${version}"
    def resultOfSearch
    try {
      resultOfSearch = this.acr.az "acr helm show --subscription ${registrySubscription} --name ${registryName} ${this.chartName} --version ${version} --query version -o tsv"
    } catch(ignored) {
      resultOfSearch = notFoundMessage
    }
    this.steps.echo "Searched remote repo ${registryName}, result was ${resultOfSearch}"

    if (resultOfSearch == notFoundMessage) {
      this.steps.echo "Publishing new version of ${this.chartName}"

      this.steps.sh "helm package ${this.chartLocation}"
      this.acr.az "acr helm push --subscription ${registrySubscription} --name ${registryName} ${this.chartName}-${version}.tgz"

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

    def allOptions = ["--install", "--wait", "--timeout 500", this.tlsOptions] + (options == null ? [] : options)
    def allValues = values.flatten()
    this.execute("upgrade", "${this.chartName}-${imageTag} ${this.chartLocation}", allValues, allOptions)
  }

  def dependencyUpdate() {
    this.execute("dependency update", this.chartLocation)
  }

  def delete(String imageTag) {
    this.execute("delete", "${this.chartName}-${imageTag}", null, ["--purge", this.tlsOptions])
  }

  def exists(String imageTag, String namespace) {
    def deployments = this.execute("list", "", null, ["-q", "--namespace ${namespace}", this.tlsOptions])
    return deployments != null && deployments.toString().contains("${this.chartName}-${imageTag}")
  }

  def history(String imageTag) {
    this.execute("history", "${this.chartName}-${imageTag}", null, ["-o json", this.tlsOptions])
  }

  def hasAnyDeployed(String imageTag, String namespace) {
    if (!exists(imageTag, namespace)) {
      return false
    }
    def releases = this.history(imageTag)
    return !releases || new JsonSlurper().parseText(releases).any{it.status == "DEPLOYED"}
  }

  private Object execute(String command, String name) {
    this.execute(command, name, null, null)
  }

  private Object execute(String command, String name, List<String> values, List<String> options) {
    steps.retry(3) {
      def optionsStr = "${options == null ?  '' : options.join(' ')}"
      def valuesStr = (values == null ? "" : "${' -f ' + values.join(' -f ')}")
      helm command, name, "${valuesStr} ${optionsStr}"
    }
  }

}
