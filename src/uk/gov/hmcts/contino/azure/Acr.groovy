package uk.gov.hmcts.contino.azure

import uk.gov.hmcts.contino.DockerImage

/**
 * Azure Container Registry (ACR) operations class.
 * 
 * Supports dual ACR publish mode for transitioning between registries.
 * When DUAL_ACR_PUBLISH_ENABLED is set to 'true', operations like build,
 * retagForStage, and purgeOldTags will be performed on both primary and
 * secondary registries.
 */
class Acr extends Az {

  def registryName
  def resourceGroup
  def registrySubscription
  
  // Secondary ACR for dual publish mode
  def secondaryRegistryName
  def secondaryResourceGroup
  def secondaryRegistrySubscription
  def dualPublishEnabled = false

  /**
   * Create a new instance of Acr with the given pipeline script, subscription and registry name
   *
   * @param steps
   *   the current pipeline script.
   *
   * @param subscription
   *   the current logged-in subscription name.  e.g. 'sandbox'
   *
   * @param registryName
   *   the 'resource name' of the ACR registry.  i.e. 'cnpacr' not 'cnpacr.azurecr.io'
   */
  Acr(steps, subscription, registryName, resourceGroup, registrySubscription) {
    super(steps, subscription)
    this.registryName = registryName
    this.resourceGroup = resourceGroup
    this.registrySubscription = registrySubscription
    
    // Initialize dual publish mode from environment
    initDualPublishMode()
  }

  /**
   * Initialize dual ACR publish mode from environment variables.
   * When enabled, operations will be performed on both primary and secondary registries.
   */
  private void initDualPublishMode() {
    this.dualPublishEnabled = steps.env.DUAL_ACR_PUBLISH_ENABLED?.toLowerCase() == 'true'
    
    if (this.dualPublishEnabled) {
      this.secondaryRegistryName = steps.env.SECONDARY_REGISTRY_NAME
      this.secondaryResourceGroup = steps.env.SECONDARY_REGISTRY_RESOURCE_GROUP
      this.secondaryRegistrySubscription = steps.env.SECONDARY_REGISTRY_SUBSCRIPTION
      
      if (!this.secondaryRegistryName || !this.secondaryResourceGroup || !this.secondaryRegistrySubscription) {
        steps.echo "WARNING: Dual ACR publish is enabled but secondary registry details are missing. Disabling dual publish."
        this.dualPublishEnabled = false
      } else if (this.secondaryRegistryName == this.registryName) {
        steps.echo "WARNING: Dual ACR publish is enabled but secondary registry name matches primary (${this.registryName}). Disabling dual publish."
        this.dualPublishEnabled = false
      } else {
        steps.echo "Dual ACR publish mode enabled: Primary=${registryName}, Secondary=${secondaryRegistryName}"
      }
    }
  }

  /**
   * Temporarily switch this Acr instance to operate against a different registry context.
   *
   * This is used to perform the same ACR task/run operations against the secondary registry
   * without constructing a nested Acr instance (which would re-read env and could recurse).
   */
  private void withRegistryContext(String targetRegistryName, String targetResourceGroup, String targetRegistrySubscription, Closure block) {
    def originalRegistryName = this.registryName
    def originalResourceGroup = this.resourceGroup
    def originalRegistrySubscription = this.registrySubscription

    try {
      this.registryName = targetRegistryName
      this.resourceGroup = targetResourceGroup
      this.registrySubscription = targetRegistrySubscription
      block.call()
    } finally {
      this.registryName = originalRegistryName
      this.resourceGroup = originalResourceGroup
      this.registrySubscription = originalRegistrySubscription
    }
  }

  /**
   * Check if dual ACR publish mode is enabled
   *
   * @return
   *   true if dual publish mode is enabled and properly configured
   */
  boolean isDualPublishEnabled() {
    return this.dualPublishEnabled
  }

  /**
   * Get the secondary registry name (for dual publish mode)
   *
   * @return
   *   the secondary registry name, or null if not configured
   */
  String getSecondaryRegistryName() {
    return this.secondaryRegistryName
  }

  /**
   * Log into ACR.  Can be used instead of 'docker login'.  You need to be logged into a subscription first.
   *
   * @return
   *   stdout/stderr of login command
   */
  def login() {
    this.az "acr login --name ${registryName} --subscription ${registrySubscription}"
    
    // Also login to secondary registry if dual publish is enabled
    if (this.dualPublishEnabled) {
      steps.echo "Logging into secondary ACR: ${secondaryRegistryName}"
      this.az "acr login --name ${secondaryRegistryName} --subscription ${secondaryRegistrySubscription}"
    }
  }

  /**
   * Gets the registry digest of a given image
   *
   * @param imageName
   *   the image name, including repository, image name and tag.  e.g. 'hmcts/alpine-test:sometag`
   *
   * @return
   *   The raw value of the digest e.g. sha256:c8aa9687b927cb65ced1aa7bd7756c2af5e84a79b54dd67cb91177d9071396aa
   */
  def getImageDigest(imageName) {
    def digest = this.az "acr repository show --name ${registryName} --image ${imageName} --subscription ${registrySubscription} --query [digest] -o tsv"
    return digest?.trim()
  }

  /**
   * Build an image
   *
   * @param dockerImage
   *   the docker image to build
   *
   * @return
   *   stdout of the step
   */
  def build(dockerImage) {
    build(dockerImage, "")
  }

  /**
   * Build an image
   *
   * @param dockerImage
   *   the docker image to build
   * @param additionalArgs
   *   additional arguments( start with space) to pass to acr build .
   *
   * @return
   *   stdout of the step
   */
  def build(dockerImage, additionalArgs) {
    // Build to primary registry
    this.az"acr build --no-format -r ${registryName} -t ${dockerImage.getBaseShortName()} --subscription ${registrySubscription} -g ${resourceGroup} --build-arg REGISTRY_NAME=${registryName}${additionalArgs} ."
    
    // Also build to secondary registry if dual publish is enabled
    if (this.dualPublishEnabled) {
      steps.echo "Building image to secondary ACR: ${secondaryRegistryName}"
      this.az"acr build --no-format -r ${secondaryRegistryName} -t ${dockerImage.getBaseShortName()} --subscription ${secondaryRegistrySubscription} -g ${secondaryResourceGroup} --build-arg REGISTRY_NAME=${secondaryRegistryName}${additionalArgs} ."
    }
  }

  // ==================== Private Helper Methods ====================

  /**
   * Get the managed identity assigned to this ACR
   *
   * @return
   *   a map with resourceId and clientId, or null if no identity is assigned
   */
  private Map getManagedIdentity() {
    try {
      def identityJson = this.az "acr identity show --name ${registryName} --resource-group ${resourceGroup} --subscription ${registrySubscription} -o json"
      
      // Handle empty or null response (ACR without managed identity)
      if (!identityJson || identityJson.trim().isEmpty()) {
        return null
      }
      
      def identity = steps.readJSON(text: identityJson)
      
      // Check for user-assigned identities first
      if (identity.userAssignedIdentities) {
        def firstIdentity = identity.userAssignedIdentities.entrySet().iterator().next()
        return [
          resourceId: firstIdentity.key,
          clientId: firstIdentity.value.clientId
        ]
      }
      
      // Fall back to system-assigned identity
      if (identity.principalId) {
        return [
          resourceId: '[system]',
          clientId: identity.principalId
        ]
      }
      
      return null
    } catch (Exception e) {
      steps.echo "Warning: Could not retrieve managed identity for ACR '${registryName}': ${e.message}"
      return null
    }
  }

  /**
   * Detect if ACB template contains cross-registry pulls
   *
   * Searches for any Azure Container Registry reference (*.azurecr.io) in the ACB YAML file.
   * This catches all patterns:
   *   - Registry aliases: "registry: hmctspublic.azurecr.io"
   *   - Docker commands: "docker pull hmctsprod.azurecr.io/base/node:20-alpine"
   *   - Build arguments: "--cache-from hmctsprod.azurecr.io/..."
   *
   * @param acbFilePath
   *   the path to the ACB template file
   *
   * @return
   *   a map with crossRegistry (boolean) and registries (list of external registry URLs)
   */
  private Map detectCrossRegistryPulls(String acbFilePath) {
    def acbContent = steps.readFile(acbFilePath)
    def externalRegistries = [] as Set
    
    // Match any ACR reference: extract registry name from "registryname.azurecr.io"
    // This catches all patterns: docker pull, registry aliases, cache-from, etc.
    def acrPattern = ~/([a-zA-Z0-9.-]+)\.azurecr\.io/
    (acbContent =~ acrPattern).each { match ->
      def registryUrl = match[0]  // Full URL: "hmctsprod.azurecr.io"
      // Only add if it's not the current registry
      if (registryUrl != registryName + '.azurecr.io') {
        externalRegistries.add(registryUrl)
      }
    }
    
    return [
      crossRegistry: !externalRegistries.isEmpty(),
      registries: externalRegistries.toList()
    ]
  }

  /**
   * Check if an ACR task exists
   *
   * @param taskName
   *   the name of the task to check
   *
   * @return
   *   true if task exists, false otherwise
   */
  private boolean taskExists(String taskName) {
    try {
      this.az "acr task show --name ${taskName} --registry ${registryName} --subscription ${registrySubscription}"
      return true
    } catch (Exception e) {
      return false
    }
  }

  /**
   * Check if an ACR task has a specific identity assigned
   *
   * @param taskName
   *   the name of the task to check
   * @param identityResourceId
   *   the resource ID of the identity to check for
   *
   * @return
   *   true if the task has the identity assigned, false otherwise
   */
  private boolean taskHasIdentity(String taskName, String identityResourceId) {
    try {
      def taskJson = this.az "acr task show --name ${taskName} --registry ${registryName} --subscription ${registrySubscription} -o json"
      def task = steps.readJSON(text: taskJson)
      
      // Check if the task has the identity in its identity configuration
      if (task.identity?.userAssignedIdentities) {
        return task.identity.userAssignedIdentities.containsKey(identityResourceId)
      }
      
      return false
    } catch (Exception e) {
      return false
    }
  }

  /**
   * Check if credentials are configured for a task
   *
   * @param taskName
   *   the name of the task
   * @param loginServer
   *   the login server to check for (e.g., hmctsprod.azurecr.io)
   *
   * @return
   *   true if credentials are configured, false otherwise
   */
  private boolean credentialsExist(String taskName, String loginServer) {
    try {
      def credentials = this.az "acr task credential list --name ${taskName} --registry ${registryName} --subscription ${registrySubscription} -o json"
      return credentials?.contains(loginServer)
    } catch (Exception e) {
      return false
    }
  }

  /**
   * Create an ACR task
   *
   * @param taskName
   *   the name of the task to create
   * @param acbFile
   *   the ACB YAML file to use
   */
  private void createTask(String taskName, String acbFile) {
    steps.echo "Creating ACR task '${taskName}'"
    this.az "acr task create --name ${taskName} --registry ${registryName} --subscription ${registrySubscription} --file ${acbFile} --context /dev/null"
  }

  /**
   * Assign managed identity to an existing ACR task
   *
   * This method assigns the ACR's managed identity to a task, enabling cross-registry authentication.
   * 
   * IMPORTANT: Jenkins service principal needs 'Managed Identity Operator' role on the ACR's identity
   * to perform this operation.
   * 
   * Example scenario:
   *   - ACR: hmctssbox (has managed identity 'hmctssbox-identity' assigned to it)
   *   - Jenkins SP: jenkins-ptl-mi (f4e06bc2-c8a5-4643-8ce7-85023024abb8)
   *   - Required: jenkins-ptl-mi needs 'Managed Identity Operator' role on hmctssbox-identity
   *   - Why: To execute 'az acr task identity assign --identities <hmctssbox-identity-resource-id>'
   * 
   * Without this role, the assignment will fail and the build will fall back to 'az acr run',
   * which doesn't support cross-registry pulls (e.g., pulling from hmctsprod while building in hmctssbox).
   *
   * @param taskName
   *   the name of the task (e.g., 'default-acr-build' or 'toffee-api-build')
   * @param identityResourceId
   *   the resource ID of the ACR's managed identity (e.g., /subscriptions/.../hmctssbox-identity)
   *
   * @return
   *   true if identity was successfully assigned, false otherwise
   */
  private boolean assignIdentityToTask(String taskName, String identityResourceId) {
    try {
      steps.echo "Assigning identity to task '${taskName}'"
      // Use the dedicated identity assign command
      this.az "acr task identity assign --name ${taskName} --registry ${registryName} --subscription ${registrySubscription} --identities ${identityResourceId}"
      steps.echo "✓ Successfully assigned identity to task"
      return true
    } catch (Exception e) {
      steps.echo "════════════════════════════════════════════════════════════════════════"
      steps.echo "ERROR: Cannot assign managed identity to ACR task"
      steps.echo "════════════════════════════════════════════════════════════════════════"
      steps.echo ""
      steps.echo "Error: ${e.message}"
      steps.echo ""
      steps.echo "Possible causes:"
      steps.echo "1. Jenkins service principal needs 'Managed Identity Operator' role"
      steps.echo "   Verify: az role assignment list --assignee f4e06bc2-c8a5-4643-8ce7-85023024abb8 --scope ${identityResourceId}"
      steps.echo ""
      steps.echo "2. Jenkins service principal needs 'Contributor' role on the ACR"
      steps.echo "   Verify: az role assignment list --assignee f4e06bc2-c8a5-4643-8ce7-85023024abb8 --all"
      steps.echo ""
      steps.echo "3. Role assignment needs time to propagate (wait 5-10 minutes)"
      steps.echo "════════════════════════════════════════════════════════════════════════"
      return false
    }
  }

  /**
   * Add credentials for cross-registry authentication
   *
   * @param taskName
   *   the name of the task
   * @param loginServer
   *   the external registry login server (e.g., hmctsprod.azurecr.io)
   * @param identityClientId
   *   the client ID of the user-assigned managed identity
   */
  private void addTaskCredentials(String taskName, String loginServer, String identityClientId) {
    steps.echo "Adding credentials for ${loginServer} to task '${taskName}'"
    this.az "acr task credential add --name ${taskName} --registry ${registryName} --subscription ${registrySubscription} --login-server ${loginServer} --use-identity ${identityClientId}"
  }

  /**
   * Execute a quick ACR run (without task-based authentication)
   */
  private void quickRun() {
    this.az "acr run -r ${registryName} -g ${resourceGroup} --subscription ${registrySubscription} ."
  }

  /**
   * Run ACR task with cross-registry authentication support
   *
   * @param taskName
   *   the name of the task
   * @param setArgs
   *   optional --set arguments for the task
   */
  private void runTask(String taskName, String setArgs = "") {
    def setArgsStr = setArgs ? " ${setArgs}" : ""
    // Provide the current directory as context when running the task
    this.az "acr task run --name ${taskName} --registry ${registryName} --subscription ${registrySubscription} --context .${setArgsStr}"
  }

  /**
   * Handle ACR execution with cross-registry authentication support
   *
   * @param acbFilePath
   *   the path to the ACB YAML file
   * @param taskName
   *   the name of the task to create/use
   * @param setArgs
   *   optional --set arguments for the task (only used for task-based execution)
   */
  private void handleAcrExecution(String acbFilePath, String taskName, String setArgs = "") { 
    // Check if ACB file exists and detect cross-registry pulls
    if (!steps.fileExists(acbFilePath)) {
      quickRun()
      return
    }

    def crossRegistryInfo = detectCrossRegistryPulls(acbFilePath)
    
    if (!crossRegistryInfo.crossRegistry) {
      // No cross-registry pulls, use quick run
      quickRun()
      return
    }

    steps.echo "Cross-registry pull detected for: ${crossRegistryInfo.registries.join(', ')}"
    
    // Get managed identity from the ACR itself
    def identity = getManagedIdentity()
    
    if (!identity) {
      steps.echo "Warning: Cross-registry authentication requires a managed identity assigned to ACR '${registryName}'. Falling back to (az acr run)."
      quickRun()
      return
    }
    
    // Create or update task with the latest ACB file
    def taskAlreadyExists = taskExists(taskName)
    if (!taskAlreadyExists) {
      createTask(taskName, acbFilePath)
    } else {
      // Update existing task with new ACB file to pick up template changes
      steps.echo "Updating ACR task '${taskName}' with latest ACB file"
      this.az "acr task update --name ${taskName} --registry ${registryName} --subscription ${registrySubscription} --file ${acbFilePath}"
    }
    
    // Check if task has the identity assigned, if not, assign it
    if (!taskHasIdentity(taskName, identity.resourceId)) {
      def identityAssigned = assignIdentityToTask(taskName, identity.resourceId)
      if (!identityAssigned) {
        steps.echo "Falling back to quick run due to permission issues"
        quickRun()
        return
      }
    }
    
    // Add credentials for each external registry if not already configured
    crossRegistryInfo.registries.each { externalRegistry ->
      if (!credentialsExist(taskName, externalRegistry)) {
        addTaskCredentials(taskName, externalRegistry, identity.clientId)
      }
    }
    
    // Run the task
    runTask(taskName, setArgs)
  }

  // ==================== Public Methods ====================

  /**
   * Run ACR scripts using the current subscription,
   * registry name and resource group.
   * Automatically detects cross-registry pulls and uses task-based execution when needed.
   *
   * @return
   *   stdout of the step
   */
  def run() {
    handleAcrExecution("acb.yaml", "default-acr-build")

    if (this.dualPublishEnabled) {
      steps.echo "Running ACR build script on secondary registry: ${secondaryRegistryName}"
      withRegistryContext(this.secondaryRegistryName, this.secondaryResourceGroup, this.secondaryRegistrySubscription) {
        handleAcrExecution("acb.yaml", "default-acr-build")
      }
    }
  }

  def runWithTemplate(String acbTemplateFilePath, DockerImage dockerImage) {
    def defaultAcrScriptFilePath = "acb.yaml"
    
    // Generate acb.yaml from template by replacing placeholders
    steps.sh(
      script: "sed -e \"s@{{CI_IMAGE_TAG}}@${dockerImage.getBaseShortName()}@g\" -e \"s@{{REGISTRY_NAME}}@${registryName}@g\" ${acbTemplateFilePath} > ${defaultAcrScriptFilePath}",
      returnStdout: true
    )?.trim()
    
    // Use a unique task name per build to avoid conflicts between parallel builds
    // Format: repo-identifier-build (e.g., plum-frontend-1347-build, plum-frontend-staging-build)
    // Note: master branch uses "staging" as imageTag, not "master"
    def repoName = dockerImage.getRepositoryName().replaceAll('/', '-')
    def imageTag = dockerImage.getImageTag()
    
    // Extract just the PR number for cleaner task names (pr-1347 -> 1347)
    def taskIdentifier = imageTag
    if (imageTag =~ /^pr-(\d+)$/) {
      taskIdentifier = (imageTag =~ /^pr-(\d+)$/)[0][1]
    }
    
    def taskName = "${repoName}-${taskIdentifier}-build"
    def setArgs = "--set CI_IMAGE_TAG=${dockerImage.getBaseShortName()} --set REGISTRY_NAME=${registryName}"
    
    handleAcrExecution(defaultAcrScriptFilePath, taskName, setArgs)

    if (this.dualPublishEnabled) {
      steps.echo "Running ACB template build on secondary registry: ${secondaryRegistryName}"
      withRegistryContext(this.secondaryRegistryName, this.secondaryResourceGroup, this.secondaryRegistrySubscription) {
        // Regenerate acb.yaml for the secondary registry so any templated REGISTRY_NAME matches.
        steps.sh(
          script: "sed -e \"s@{{CI_IMAGE_TAG}}@${dockerImage.getBaseShortName()}@g\" -e \"s@{{REGISTRY_NAME}}@${registryName}@g\" ${acbTemplateFilePath} > ${defaultAcrScriptFilePath}",
          returnStdout: true
        )?.trim()

        def secondarySetArgs = "--set CI_IMAGE_TAG=${dockerImage.getBaseShortName()} --set REGISTRY_NAME=${registryName}"
        handleAcrExecution(defaultAcrScriptFilePath, taskName, secondarySetArgs)
      }
    }
  }

  /**
   * get the hostname of the ACR
   *
   * @return
   *   the hostname. e.g. cnpacr.azurecr.io
   */
  def getHostname() {
    def host = this.az "acr show -n ${registryName} --subscription ${registrySubscription} --query loginServer -otsv"
    return host?.trim()
  }

  /**
   * Retags an image in the registry with an appended suffix
   *
   * e.g.: <image-name>:latest will also be tagged as <image-name>:latest-dfb02
   *
   * @param stage
   *   a deployment stage indicating to which environments the image has been promoted to
   *
   * @param dockerImage
   *   the docker image to build
   *
   * @return
   *   stdout of the step
   */
  def retagForStage(stage, dockerImage) {
    def additionalTag = dockerImage.getShortName(stage)
    // Non master branch builds like preview are tagged with the base tag
    def baseTag = (stage == DockerImage.DeploymentStage.PR || stage == DockerImage.DeploymentStage.PREVIEW || dockerImage.imageTag == 'staging')
      ? dockerImage.getBaseTaggedName() : dockerImage.getTaggedName()
    
    // Retag in primary registry
    this.az "acr import --force -n ${registryName} -g ${resourceGroup} --subscription ${registrySubscription} --source ${baseTag} -t ${additionalTag}"?.trim()
    
    // Also retag in secondary registry if dual publish is enabled
    if (this.dualPublishEnabled) {
      steps.echo "Promoting image to secondary ACR: ${secondaryRegistryName}"
      // Need to use the secondary registry's hostname in the source
      def secondaryBaseTag = baseTag.replace("${registryName}.azurecr.io", "${secondaryRegistryName}.azurecr.io")
      this.az "acr import --force -n ${secondaryRegistryName} -g ${secondaryResourceGroup} --subscription ${secondaryRegistrySubscription} --source ${secondaryBaseTag} -t ${additionalTag}"?.trim()
    }
  }

  def untag(DockerImage dockerImage) {
    if (!dockerImage.isLatest()) {
      this.az "acr repository untag -n ${registryName} -g ${resourceGroup} --subscription ${registrySubscription} --image ${dockerImage.getShortName()}"
    }
  }

  def hasTag(dockerImage) {
      return hasRepoTag(dockerImage.getTag(), dockerImage.getRepositoryName())
  }

  def hasTag(DockerImage.DeploymentStage stage, DockerImage dockerImage) {
    String tag = dockerImage.getTag(stage)
    return hasRepoTag(tag, dockerImage.getRepositoryName())
  }

  private boolean hasRepoTag(String tag, String repository) {
    // staging and latest are not really tags for our purposes, it just marks the most recent master build before and after tests are run in AAT.
    if (tag in ['staging' , 'latest'] ) {
      steps.echo "Warning: matching '${tag}' tag for ${repository}"
    }

    def tagFound = false
    try {
      def tags = this.az "acr repository show-tags -n ${registryName} --subscription ${registrySubscription} --repository ${repository}"
      tagFound = tags.contains(tag.replace("\n", ""))
      // steps.echo "Current tags: ${tags}. Is ${tag} available? ... ${tagFound}"
    } catch (noTagsError) {
    } // Do nothing -> return false

    return tagFound
  }

  def purgeOldTags(stage, dockerImage) {
    String purgeTag = stage == DockerImage.DeploymentStage.PR ? dockerImage.getImageTag() : stage.getLabel()
    String filterPattern = dockerImage.getRepositoryName().concat(":^").concat(purgeTag).concat("-.*")
    
    // Purge from primary registry
    this.az "acr run --registry ${registryName} --subscription ${registrySubscription} --cmd \"acr purge --filter ${filterPattern} --ago ${stage.purgeAgo} --keep ${stage.purgeKeep} --untagged --concurrency 5\" /dev/null"
    
    // Also purge from secondary registry if dual publish is enabled
    if (this.dualPublishEnabled) {
      steps.echo "Purging old tags from secondary ACR: ${secondaryRegistryName}"
      this.az "acr run --registry ${secondaryRegistryName} --subscription ${secondaryRegistrySubscription} --cmd \"acr purge --filter ${filterPattern} --ago ${stage.purgeAgo} --keep ${stage.purgeKeep} --untagged --concurrency 5\" /dev/null"
    }
  }
}
