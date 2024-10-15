package uk.gov.hmcts.contino

import uk.gov.hmcts.contino.azure.Acr
import groovy.json.JsonSlurper


class Helm {

  public static final String HELM_RESOURCES_DIR = "charts"
  def steps
  def acr
  def helm = { cmd, name, options -> return this.steps.sh(label: "helm $cmd", script: "helm $cmd $name $options", returnStdout: true)}

  def subscription
  def subscriptionId
  def resourceGroup
  def registryName
  String chartLocation
  def chartName
  def notFoundMessage = "Not found"
  String registrySubscription
  def namespace

  Helm(steps, String chartName) {
    this.steps = steps
    this.subscription = this.steps.env.SUBSCRIPTION_NAME
    this.subscriptionId = this.steps.env.ARM_SUBSCRIPTION_ID
    this.resourceGroup = this.steps.env.AKS_RESOURCE_GROUP
    this.registryName = this.steps.env.REGISTRY_NAME
    this.registrySubscription = this.steps.env.REGISTRY_SUBSCRIPTION
    this.acr = new Acr(this.steps, subscription, registryName, resourceGroup, registrySubscription)
    this.chartLocation = "${HELM_RESOURCES_DIR}/${chartName}"
    this.chartName = chartName
    this.namespace = this.steps.env.TEAM_NAMESPACE
  }

  def setup() {
    authenticateAcr()
    configureAcr()
    removeRepo()
    addRepo()
  }

  def configureAcr() {
    this.acr.az "configure --defaults acr=${registryName}"
  }

  def removeRepo() {
    this.steps.echo "Clear out helm repo before re-adding"
    this.steps.sh(label: "helm repo rm ${registryName}", script: 'helm repo rm $REGISTRY_NAME || echo "Helm repo may not exist on disk, skipping remove"', env: [REGISTRY_NAME: registryName])
  }
  
  def addRepo() {
    this.acr.az "acr helm repo add --subscription ${registrySubscription} --name ${registryName}"
  }

  def authenticateAcr() {
    this.acr.az "acr login --name ${registryName}"
  }

  def publishIfNotExists(List<String> values) {
    configureAcr()
    removeRepo()
    authenticateAcr()
    dependencyUpdate()
    lint(values)

    def version = this.steps.sh(script: "helm inspect chart ${this.chartLocation} | grep ^version | cut -d ':' -f 2", returnStdout: true).trim()
    this.steps.echo "Version of chart locally is: ${version}"
    def resultOfSearch
    try {
        addRepo()
        this.steps.sh(script: "helm pull ${registryName}/${this.chartName} --version ${version} -d .", returnStdout: true).trim()
        resultOfSearch = version
    } catch(ignored) {
        resultOfSearch = notFoundMessage
    }
    this.steps.echo "Searched remote repo ${registryName}, result was ${resultOfSearch}"

    if (resultOfSearch == notFoundMessage) {
      this.steps.echo "Publishing new version of ${this.chartName}"

      this.steps.sh "helm package ${this.chartLocation}"
      this.steps.sh(script: "helm push ${this.chartLocation}-${version}.tgz oci://${registryName}.azurecr.io/helm/${this.chartName}")

      this.steps.echo "Published ${this.chartName}-${version} to ${registryName}"
    } else {
        this.steps.echo "Chart already published, skipping publish, bump the version in ${this.chartLocation}/Chart.yaml if you want it to be published"
    }
  }

  def publishToGitIfNotExists(List<String> values) {
    authenticateAcr()
    addRepo()
    lint(values)

    def version = this.steps.sh(script: "helm inspect chart ${this.chartLocation}  | grep ^version | cut -d  ':' -f 2", returnStdout: true).trim()
    this.steps.echo "Version of chart locally is: ${version}"

    this.steps.writeFile file: 'push-helm-charts-to-git.sh', text: this.steps.libraryResource('uk/gov/hmcts/helm/push-helm-charts-to-git.sh')

    this.steps.withCredentials([this.steps.usernamePassword(credentialsId: this.steps.env.GIT_CREDENTIALS_ID, passwordVariable: 'BEARER_TOKEN', usernameVariable: 'APP_ID')]) {
      this.steps.sh (
        """
        chmod +x push-helm-charts-to-git.sh
        ./push-helm-charts-to-git.sh ${this.chartLocation} ${this.chartName} $version
        """
      )

    this.steps.sh 'rm push-helm-charts-to-git.sh'
    }
  }

  def lint(List<String> values) {
    this.execute("lint", this.chartLocation, values, null)
  }

  def installOrUpgrade(String imageTag, List<String> values, List<String> options) {
    if (!values) {
      throw new RuntimeException("Helm charts need at least a values file (none given).")
    }
    def releaseName = "${this.chartName}-${imageTag}"
    dependencyUpdate()
    lint(values)

    this.steps.writeFile file: 'aks-debug-info.sh', text: this.steps.libraryResource('uk/gov/hmcts/helm/aks-debug-info.sh')

    this.steps.sh ("chmod +x aks-debug-info.sh")
    def optionsStr = (options + ["--install", "--wait", "--timeout 1250s"]).join(' ')
    def valuesStr =  "${' -f ' + values.flatten().join(' -f ')}"
    steps.sh(label: "helm upgrade", script: "helm upgrade ${releaseName}  ${this.chartLocation} ${valuesStr} ${optionsStr} || ./aks-debug-info.sh ${releaseName} ${this.namespace} ")
    this.steps.sh 'rm aks-debug-info.sh'
  }

  def dependencyUpdate() {
    this.execute("dependency update", this.chartLocation)
  }

  def delete(String imageTag, String namespace) {
    this.execute("uninstall", "${this.chartName}-${imageTag}", null, ["--namespace ${namespace}"])
  }

  def exists(String imageTag, String namespace) {
    def deployments = this.execute("list", "", null, ["--all", "-q", "--namespace ${namespace}"])
    return deployments != null && deployments.toString().contains("${this.chartName}-${imageTag}")
  }

  def history(String imageTag, String namespace) {
    this.execute("history", "${this.chartName}-${imageTag}", null, ["--namespace ${namespace}", "-o json"])
  }

  def hasAnyFailedToDeploy(String imageTag, String namespace) {
    if (!exists(imageTag, namespace)) {
      this.steps.echo "No release deployed for: $imageTag in namespace $namespace"
      return false
    }
    def releases = this.history(imageTag, namespace)
    this.steps.echo releases
    return !releases || new JsonSlurper().parseText(releases).any { it.status?.toLowerCase() == "failed" ||
                                                                    it.status?.toLowerCase() == "pending-upgrade" ||
                                                                    it.status?.toLowerCase() == "pending-install" }
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