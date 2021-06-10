package uk.gov.hmcts.contino

import uk.gov.hmcts.contino.azure.Acr

class DockerImage {

  public static final String TEST_REPO = "test"

  // environment the image has been promoted to
  enum DeploymentStage {
    PR('pr'),
    STAGING('staging'),
    AAT('aat'),
    PREVIEW('preview'),
    PROD('prod'),
    LATEST('latest')

    final String label

    private DeploymentStage(String label) {
      this.label = label
    }
  }

  def product
  def component
  def imageTag  // image tag, based on the current build branch name e.g. 'latest', 'pr-77'
  Acr acr
  def commit
  def registryHost
  def lastcommittime


  DockerImage(product, component, acr, tag, commit, lastcommittime) {
    this.product = product
    this.component = component
    this.imageTag = tag
    this.acr = acr
    this.commit = commit?.substring(0, 7)
    this.lastcommittime = lastcommittime
  }

  /**
   * Get the full image name, including the tag. Use when you
   * need to build an image
   *
   * @return
   *   the image name, e.g.: 'cnpacr.azurecr.io/hmcts/alpine-test:sometag-commit'
   */
  def getTaggedName() {
    return this.getRegistryHostname().concat('/')
      .concat(this.getShortName())
  }

  /**
   * Get the full image name, including the tag but excluding the commit.
   * Use when you need to retag (promote) a pr image
   *
   * @return
   *   the image name, e.g.: 'cnpacr.azurecr.io/hmcts/alpine-test:sometag'
   */
  def getBaseTaggedName() {
    return this.getRegistryHostname().concat('/')
      .concat(this.getBaseShortName())
  }

  /**
   * Get the name of the service to be used in AKS.
   *
   * @return
   *   The service name, such as 'rhubarb-front-end-pr-77'
   */
  def getAksServiceName() {
    return this.product.concat('-').concat(this.component)
      .concat('-').concat(this.imageTag)
  }

  /**
   * Get the full image name including digest.  Useful for pulling by digest
   *
   * @param acr
   *   an Acr instance
   *
   * @return
   *   the image name/digest.  e.g.:
   *     cnpacr.azurecr.io/hmcts/alpine-test@sha256:c8aa9687b927cb65ced1aa7bd7756c2af5e84a79b54dd67cb91177d9071396aa
   */
  def getDigestName() {
    def digest = acr.getImageDigest(this.getShortName())
    if (!digest) {
      // this could result in deploying an image of unknown state, so stop right here
      throw new IllegalStateException("A digest is not available for this image.  Has it been pushed?")
    }

    return this.getRegistryHostname().concat('/')
      .concat(this.product).concat('/')
      .concat(this.component).concat('@')
      .concat(digest)
  }

  /**
   * Get the image tag, based on the current build branch name and commit
   *
   * @return
   *   the tag e.g. 'latest', 'pr-77-tr123456'
   */
  def getTag() {
    return getTag(this.imageTag)
  }

  def getTag(DeploymentStage stage) {
    // if it's a PR use the full imageTag (e.g. pr-42)
    if (stage == DeploymentStage.PR) {
      return getTag(this.imageTag)
    }
    return getTag(stage.label)
  }

  def getTag(String imageTag) {
    return (imageTag ==  'latest' ? imageTag : "${imageTag}-${this.commit}-${this.lastcommittime}")
  }

  def isLatest() {
    return getTag() == 'latest'
  }

  /**
   * Get the 'short name' of the image, without the registry prefix
   *
   * @return
   *   the short name. e.g. product/component:branch-commit or product/component:latest
   */
  def getShortName() {
    return shortName(this.imageTag)
  }

  def getShortName(DeploymentStage stage) {
    // if it's a PR use the full imageTag (e.g. pr-42)
    if (stage == DeploymentStage.PR) {
      return shortName(this.imageTag)
    }
    return shortName(stage.label)
  }

  private def shortName(String imageTag) {
    return repositoryName().concat(':')
      .concat(getTag(imageTag))
  }

  /**
   * Get the 'short name' of the image, without the registry prefix ,commit suffix is added only for staging.
   * Use this while resolving name for the base image for building or re-tagging.
   *
   * @return
   *   the short name. e.g. hmcts/product-component:branch
   */
  def getBaseShortName() {
    def baseShortName = this.imageTag == 'staging' ? "${imageTag}-${this.commit}-${this.lastcommittime}" : imageTag
    return repositoryName().concat(':')
      .concat(baseShortName)
  }

  def getRepositoryName() {
    return repositoryName()
  }

  private def repositoryName() {
    return this.product
      .concat('/')
      .concat(this.component)
  }

  private def getRegistryHostname() {
    if (!this.registryHost) {
      this.registryHost = this.acr.getHostname()
    }
    return this.registryHost
  }
}
