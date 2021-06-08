package uk.gov.hmcts.contino.azure

import uk.gov.hmcts.contino.DockerImage

class Acr extends Az {

  def registryName
  def resourceGroup
  def registrySubscription

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
  }

  /**
   * Log into ACR.  Can be used instead of 'docker login'.  You need to be logged into a subscription first.
   *
   * @return
   *   stdout/stderr of login command
   */
  def login() {
    this.az "acr login --name ${registryName} --subscription ${registrySubscription}"
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
  def build(DockerImage dockerImage) {
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
  def build(DockerImage dockerImage, String additionalArgs) {
    this.az "acr build --no-format -r ${registryName} -t ${dockerImage.getBaseShortName()} --subscription ${registrySubscription} -g ${resourceGroup} --build-arg REGISTRY_NAME=${registryName}${additionalArgs} ."
  }

  /**
   * Run ACR scripts using the current subscription,
   * registry name and resource group
   *
   * @return
   *   stdout of the step
   */
  def run() {
    this.az "acr run -r ${registryName} -g ${resourceGroup} --subscription ${registrySubscription} ."
  }

  def runWithTemplate(String acbTemplateFilePath, DockerImage dockerImage) {
    def defaultAcrScriptFilePath = "acb.yaml"
    steps.sh(
      script: "sed -e \"s@{{CI_IMAGE_TAG}}@${dockerImage.getBaseShortName()}@g\" -e \"s@{{REGISTRY_NAME}}@${registryName}@g\" ${acbTemplateFilePath} > ${defaultAcrScriptFilePath}",
      returnStdout: true
    )?.trim()
    this.run()
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
  def retagForStage(DockerImage.DeploymentStage stage, DockerImage dockerImage) {
    def additionalTag = dockerImage.getShortName(stage)
    // Non master branch builds like preview are tagged with the base tag
    def baseTag = (stage == DockerImage.DeploymentStage.PR || stage == DockerImage.DeploymentStage.PREVIEW || dockerImage.imageTag == 'staging')
      ? dockerImage.getBaseTaggedName() : dockerImage.getTaggedName()
    this.az "acr import --force -n ${registryName} -g ${resourceGroup} --subscription ${registrySubscription} --source ${baseTag} -t ${additionalTag}"?.trim()
  }

  def untag(DockerImage dockerImage) {
    if (!dockerImage.isLatest()) {
      this.az "acr repository untag -n ${registryName} -g ${resourceGroup} --subscription ${registrySubscription} --image ${dockerImage.getShortName()}"
    }
  }

  def hasTag(DockerImage dockerImage) {
    // on the master branch we search for an AAT tagged image with the same commit hash
    if (dockerImage.getTag().startsWith("staging")) {
      return hasTag(DockerImage.DeploymentStage.AAT, dockerImage)
    } else {
      return hasRepoTag(dockerImage.getTag(), dockerImage.getRepositoryName())
    }
  }

  def hasTag(DockerImage.DeploymentStage stage, DockerImage dockerImage) {
    String tag = dockerImage.getTag(stage)
    return hasRepoTag(tag, dockerImage.getRepositoryName())
  }

  private def hasRepoTag(String tag, String repository) {
    // staging and latest are not really tags for our purposes, it just marks the most recent master build before and after tests are run in AAT.
    if (tag in ['staging' , 'latest'] ) {
      steps.echo "Warning: matching '${tag}' tag for ${repository}"
    }

    def tagFound = false
    try {
      def tags = this.az "acr repository show-tags -n ${registryName} --subscription ${registrySubscription} --repository ${repository}"
      tagFound = tags.contains(tag)
      steps.echo "Current tags: ${tags}. Is ${tag} available? ... ${tagFound}"
    } catch (noTagsError) {
    } // Do nothing -> return false

    return tagFound
  }

}
