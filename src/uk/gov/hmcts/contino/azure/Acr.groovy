package uk.gov.hmcts.contino.azure

import uk.gov.hmcts.contino.DockerImage

class Acr extends Az {

  def registryName
  def resourceGroup

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
  Acr(steps, subscription, registryName, resourceGroup) {
    super(steps, subscription)
    this.registryName = registryName
    this.resourceGroup = resourceGroup
  }

  /**
   * Log into ACR.  Can be used instead of 'docker login'.  You need to be logged into a subscription first.
   *
   * @return
   *   stdout/stderr of login command
   */
  def login() {
    this.az "acr login --name ${registryName}"
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
    def digest = this.az "acr repository show --name ${registryName} --image ${imageName} --query [digest] -otsv"
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
    this.az "acr build --no-format -r ${registryName} -t ${dockerImage.getShortName()} -g ${resourceGroup} --build-arg REGISTRY_NAME=${registryName} ."
  }

  /**
   * Run ACR scripts using the current subscription,
   * registry name and resource group
   *
   * @return
   *   stdout of the step
   */
  def run() {
    this.az "acr run -r ${registryName} -g ${resourceGroup} ."
  }

  def runWithTemplate(String acbTemplateFilePath, DockerImage dockerImage) {
    def defaultAcrScriptFilePath = "acb.yaml"
    steps.sh(
      script: "sed -e \"s@{{CI_IMAGE_TAG}}@${dockerImage.getShortName()}@g\" -e \"s@{{REGISTRY_NAME}}@${registryName}@g\" ${acbTemplateFilePath} > ${defaultAcrScriptFilePath}",
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
    def host = this.az "acr show -n ${registryName} --query loginServer -otsv"
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
    this.az "acr import --force -n ${registryName} -g ${resourceGroup} --source ${dockerImage.getTaggedName()} -t ${additionalTag}"?.trim()
  }

  def hasTag(DockerImage dockerImage) {
    return hasTag(dockerImage.getTag(), dockerImage.getRepositoryName())
  }

  def hasTag(String imageTag, DockerImage dockerImage) {
    String tag = dockerImage.getTag(imageTag)
    return hasTag(tag, dockerImage.getRepositoryName())
  }

  def hasTag(DockerImage.DeploymentStage stage, DockerImage dockerImage) {
    String tag = dockerImage.getTag(stage)
    return hasTag(tag, dockerImage.getRepositoryName())
  }

  def hasTag(String tag, String repository) {
    // latest is not really a tag for our purposes, it just marks the most recent tag
    if (tag == 'latest') {
      return false
    }
    def tags = this.az "acr repository show-tags -n ${registryName} -g ${resourceGroup} --repository ${repository}"
    return tags.contains(tag)
  }

}
