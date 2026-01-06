package uk.gov.hmcts.contino

import uk.gov.hmcts.contino.azure.Acr
import uk.gov.hmcts.contino.Docker
import groovy.json.JsonSlurperClassic

/**
 * Helm chart management class.
 * 
 * Supports dual ACR publish mode for transitioning between registries.
 * When DUAL_ACR_PUBLISH_ENABLED is set to 'true', Helm charts will be
 * published to both primary and secondary ACR registries.
 */
class Helm {

  public static final String HELM_RESOURCES_DIR = 'charts'
  def steps
  def acr
  def docker
  def helm = { cmd, name, options -> return this.steps.sh(label: "helm $cmd", script: "helm $cmd $name $options", returnStdout: true) }

  def subscription
  def subscriptionId
  def resourceGroup
  def registryName
  def dockerHubUsername
  def dockerHubPassword
  String chartLocation
  def chartName
  def notFoundMessage = 'Not found'
  String registrySubscription
  def namespace
  
  // Secondary ACR for dual publish mode
  def secondaryRegistryName
  def secondaryResourceGroup
  def secondaryRegistrySubscription
  def dualPublishEnabled = false

  Helm(steps, String chartName) {
    this.steps = steps
    this.subscription = this.steps.env.SUBSCRIPTION_NAME
    this.subscriptionId = this.steps.env.ARM_SUBSCRIPTION_ID
    this.resourceGroup = this.steps.env.AKS_RESOURCE_GROUP
    this.registryName = this.steps.env.REGISTRY_NAME
    this.registrySubscription = this.steps.env.REGISTRY_SUBSCRIPTION
    this.docker = new Docker(this.steps)
    this.dockerHubUsername = this.steps.env.DOCKER_HUB_USERNAME
    this.dockerHubPassword = this.steps.env.DOCKER_HUB_PASSWORD
    this.acr = new Acr(this.steps, subscription, registryName, resourceGroup, registrySubscription)
    this.chartLocation = "${HELM_RESOURCES_DIR}/${chartName}"
    this.chartName = chartName
    this.namespace = this.steps.env.TEAM_NAMESPACE
    
    // Initialize dual publish mode
    initDualPublishMode()
  }

  /**
   * Initialize dual ACR publish mode from environment variables.
   */
  private void initDualPublishMode() {
    this.dualPublishEnabled = steps.env.DUAL_ACR_PUBLISH_ENABLED?.toLowerCase() == 'true'
    
    if (this.dualPublishEnabled) {
      this.secondaryRegistryName = steps.env.SECONDARY_REGISTRY_NAME
      this.secondaryResourceGroup = steps.env.SECONDARY_REGISTRY_RESOURCE_GROUP
      this.secondaryRegistrySubscription = steps.env.SECONDARY_REGISTRY_SUBSCRIPTION
      
      if (!this.secondaryRegistryName || !this.secondaryResourceGroup || !this.secondaryRegistrySubscription) {
        steps.echo "WARNING: Dual ACR publish is enabled but secondary registry details are missing for Helm. Disabling dual publish."
        this.dualPublishEnabled = false
      } else {
        steps.echo "Dual ACR publish mode enabled for Helm: Primary=${registryName}, Secondary=${secondaryRegistryName}"
      }
    }
  }

  def setup() {
    authenticateAcr()
    configureAcr()
    authenticateDockerHub()
    removeRepo()
  }

  def configureAcr() {
    this.acr.az "configure --defaults acr=${registryName}"
  }

  def removeRepo() {
    this.steps.echo 'Clear out helm repo before re-adding'
    this.steps.sh(label: "helm repo rm ${registryName}", script: 'helm repo rm $REGISTRY_NAME || echo "Helm repo may not exist on disk, skipping remove"', env: [REGISTRY_NAME: registryName])
  }

  /**
   * Detect if Chart.yaml has dependencies from external ACR registries
   *
   * @return
   *   list of external ACR registry names (e.g., ['hmctsprod'])
   */
  private List<String> detectCrossRegistryDependencies() {
    def chartYamlPath = "${this.chartLocation}/Chart.yaml"
    
    if (!steps.fileExists(chartYamlPath)) {
      return []
    }
    
    try {
      def chartYaml = steps.readFile(chartYamlPath)
      def externalRegistries = [] as Set
      
      steps.echo "Scanning Chart.yaml for OCI-based cross-registry dependencies..."
      
      // Look for OCI repository references in dependencies
      // Example: repository: oci://hmctsprod.azurecr.io/helm
      // Handles: repository: 'oci://...' or repository: "oci://..." or repository: oci://...
      def ociPattern = ~/repository:\s*['"]?oci:\/\/([a-zA-Z0-9]+)\.azurecr\.io/
      def matcher = chartYaml =~ ociPattern
      matcher.each { match ->
        def externalRegistry = match[1]
        steps.echo('Found OCI registry: ' + externalRegistry)
        // Only add if it's not the current registry
        if (externalRegistry != registryName) {
          externalRegistries.add(externalRegistry)
        } else {
          steps.echo('skipped - same as current registry')
        }
      }
      
      return externalRegistries.toList()
    } catch (Exception e) {
      steps.echo "Warning: Could not parse Chart.yaml for cross-registry dependencies: ${e.message}"
      return []
    }
  }

  def authenticateAcr() {
    // Authenticate to current registry
    this.acr.az "acr login --name ${registryName}"
    
    // Check if chart has dependencies from other ACR registries
    def externalRegistries = detectCrossRegistryDependencies()
    
    steps.echo "Detected external registries: ${externalRegistries.size() > 0 ? externalRegistries.join(', ') : 'none'}"
    
    if (externalRegistries) {
      steps.echo "Chart has dependencies from: ${externalRegistries.join(', ')}"
      externalRegistries.each { externalRegistry ->
        steps.echo "Authenticating to ${externalRegistry} ACR for chart dependencies"
        this.acr.az "acr login --name ${externalRegistry}"
      }
    }
    
    // Also authenticate to secondary registry if dual publish is enabled
    if (this.dualPublishEnabled) {
      steps.echo "Authenticating to secondary ACR for dual publish: ${secondaryRegistryName}"
      this.acr.az "acr login --name ${secondaryRegistryName}"
    }
  }

  def authenticateDockerHub() {
    this.steps.echo 'Log into Docker Hub'
    this.docker.loginDockerHub("${dockerHubUsername}", "${dockerHubPassword}")
  }

  def publishIfNotExists(List<String> values) {
    configureAcr()
    removeRepo()
    authenticateAcr()
    dependencyUpdate()
    lint(values)

    def version = this.steps.sh(script: "helm inspect chart ${this.chartLocation} | grep ^version | cut -d ':' -f 2", returnStdout: true).trim()
    this.steps.echo "Version of chart locally is: ${version}"
    
    // Check and publish to primary registry
    def primaryResult = checkAndPublishToRegistry(registryName, version)
    
    // Also publish to secondary registry if dual publish is enabled
    if (this.dualPublishEnabled) {
      this.steps.echo "Checking secondary ACR for chart: ${secondaryRegistryName}"
      checkAndPublishToRegistry(secondaryRegistryName, version)
    }
  }

  /**
   * Check if a chart version exists in a registry and publish if not.
   *
   * @param targetRegistry
   *   the registry name to publish to
   * @param version
   *   the chart version
   *
   * @return
   *   true if published, false if already exists
   */
  private boolean checkAndPublishToRegistry(String targetRegistry, String version) {
    def resultOfSearch
    try {
      this.steps.sh(script: "helm pull oci://${targetRegistry}.azurecr.io/helm/${this.chartName} --version ${version} -d .", returnStdout: true).trim()
      resultOfSearch = version
    } catch (ignored) {
      resultOfSearch = notFoundMessage
    }

    this.steps.echo "Searched remote repo ${targetRegistry}, result was ${resultOfSearch}"

    if (resultOfSearch == notFoundMessage) {
      this.steps.echo "Publishing new version of ${this.chartName} to ${targetRegistry}"

      this.steps.sh "helm package ${this.chartLocation} --destination ${this.chartLocation}"
      this.steps.sh(script: "helm push ${this.chartLocation}/${this.chartName}-${version}.tgz oci://${targetRegistry}.azurecr.io/helm/")
      this.steps.sh(script: "rm ${this.chartLocation}/${this.chartName}-${version}.tgz")
      this.steps.echo "Published ${this.chartName}-${version} to ${targetRegistry}"
      return true
    } else {
      this.steps.echo "Chart already published to ${targetRegistry}, skipping publish, bump the version in ${this.chartLocation}/Chart.yaml if you want it to be published"
      return false
    }
  }

  def publishToGitIfNotExists(List<String> values) {
    authenticateAcr()
    lint(values)

    def version = this.steps.sh(script: "helm inspect chart ${this.chartLocation}  | grep ^version | cut -d  ':' -f 2", returnStdout: true).trim()
    this.steps.echo "Version of chart locally is: ${version}"

    this.steps.writeFile file: 'push-helm-charts-to-git.sh', text: this.steps.libraryResource('uk/gov/hmcts/helm/push-helm-charts-to-git.sh')

    this.steps.withCredentials([this.steps.usernamePassword(credentialsId: this.steps.env.GIT_CREDENTIALS_ID, passwordVariable: 'BEARER_TOKEN', usernameVariable: 'APP_ID')]) {
      this.steps.sh(
        """
        chmod +x push-helm-charts-to-git.sh
        ./push-helm-charts-to-git.sh ${this.chartLocation} ${this.chartName} $version
        """
      )

      this.steps.sh 'rm push-helm-charts-to-git.sh'
    }
  }

  def lint(List<String> values) {
    this.execute('lint', this.chartLocation, values, null)
  }

  def installOrUpgrade(String imageTag, List<String> values, List<String> options) {
    if (!values) {
      throw new RuntimeException('Helm charts need at least a values file (none given).')
    }

    def releaseName = "${this.chartName}-${imageTag}"
    dependencyUpdate()
    lint(values)

    this.steps.writeFile file: 'aks-debug-info.sh', text: this.steps.libraryResource('uk/gov/hmcts/helm/aks-debug-info.sh')

    this.steps.sh('chmod +x aks-debug-info.sh')
    
    boolean onPR = new ProjectBranch(this.steps.env.BRANCH_NAME).isPR()
    def optionsStr = (options + (onPR ? ['--install', '--timeout 1250s'] : ['--install', '--wait', '--timeout 1250s'])).join(' ')
    def valuesStr =  "${' -f ' + values.flatten().join(' -f ')}"

    if (onPR) {
      this.steps.sh(label: 'helm upgrade', script: "helm upgrade ${releaseName}  ${this.chartLocation} ${valuesStr} ${optionsStr}")
      this.steps.sh(label: 'wait for install', script:
        """
        echo 'Waiting 30s for initial pod creation...'
        sleep 30

        POD_COUNT=\$(kubectl get pods -n ${this.namespace} -l app.kubernetes.io/instance=${releaseName},'!job-name' --no-headers 2>/dev/null | wc -l)

        if [ "\$POD_COUNT" -eq 0 ]; then
          echo "ℹ️  No pods found matching selector - this chart may only contain jobs/cronjobs"
          exit 0
        fi

        if kubectl get pods -n ${this.namespace} -l app.kubernetes.io/instance=${releaseName},'!job-name' -o json | \
          jq -e '.items[].status.containerStatuses[]? | select(.state.waiting.reason |
          test("ImagePullBackOff|ErrImagePull|CrashLoopBackOff|CreateContainerConfigError"))' > /dev/null 2>&1; then
           echo "❌ Critical error detected - failing fast"
           ./aks-debug-info.sh ${releaseName} ${this.namespace}
           exit 1
        fi

        echo 'Waiting for pods to be scheduled and ready...'
        kubectl wait --for=condition=ready pod \\
          -l app.kubernetes.io/instance=${releaseName},'!job-name' \\
          -n ${this.namespace} \\
          --timeout=1220s || ./aks-debug-info.sh ${releaseName} ${this.namespace}
        """)
    } else {
      this.steps.sh(label: 'helm upgrade', script: "helm upgrade ${releaseName}  ${this.chartLocation} ${valuesStr} ${optionsStr} || ./aks-debug-info.sh ${releaseName} ${this.namespace}")
    }
    this.steps.sh 'rm aks-debug-info.sh'
  }

  def dependencyUpdate() {
    this.execute('dependency update', this.chartLocation)
  }

  def delete(String imageTag, String namespace) {
    this.execute('uninstall', "${this.chartName}-${imageTag}", null, ["--namespace ${namespace}"])
  }

  def exists(String imageTag, String namespace) {
    def deployments = this.execute('list', '', null, ['--all', '-q', "--namespace ${namespace}"])
    return deployments != null && deployments.toString().contains("${this.chartName}-${imageTag}")
  }

  def history(String imageTag, String namespace) {
    this.execute('history', "${this.chartName}-${imageTag}", null, ["--namespace ${namespace}", '-o json'])
  }

  def hasAnyFailedToDeploy(String imageTag, String namespace) {
    if (!exists(imageTag, namespace)) {
      this.steps.echo "No release deployed for: $imageTag in namespace $namespace"
      return false
    }
    def releases = this.history(imageTag, namespace)
    this.steps.echo releases

    if (!releases) {
      return false
    }

    try {
      return new JsonSlurperClassic().parseText(releases).any { it.status?.toLowerCase() == 'failed' ||
                                                               it.status?.toLowerCase() == 'pending-upgrade' ||
                                                               it.status?.toLowerCase() == 'pending-install' }
    } catch (Exception e) {
      this.steps.echo "Failed to parse helm history JSON: ${e.getMessage()}"
      return false
    }
  }

  private Object execute(String command, String name) {
    this.execute(command, name, null, null)
  }

  private Object execute(String command, String name, List<String> values, List<String> options) {
    def optionsStr = "${options == null ?  '' : options.join(' ')}"
    def valuesStr = (values == null ? '' : "${' -f ' + values.join(' -f ')}")
    helm command, name, "${valuesStr} ${optionsStr}"
  }

}
