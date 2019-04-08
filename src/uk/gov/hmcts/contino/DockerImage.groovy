package uk.gov.hmcts.contino

import uk.gov.hmcts.contino.azure.Acr

class DockerImage {

  static String REPOSITORY = 'hmcts'

  // environment the image has been promoted to
  enum DeploymentStage {
    PR('pr'),
    AAT('aat'),
    PROD('prod');

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


  DockerImage(product, component, acr, tag, commit) {
    this.product = product
    this.component = component
    this.imageTag = tag
    this.acr = acr
    this.commit = commit?.substring(0, 8)
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
      .concat(REPOSITORY).concat('/')
      .concat(this.product).concat('-')
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
    return (imageTag == 'latest' ? imageTag : "${imageTag}-${this.commit}")
  }

  def isLatest() {
    return getTag() == 'latest'
  }

  /**
   * Get the 'short name' of the image, without the registry prefix
   *
   * @return
   *   the short name. e.g. hmcts/product-component:branch-commit or hmcts/product-component:latest
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
   * Get the 'short name' of the image, without the registry prefix and the commit suffix
   *
   * @return
   *   the short name. e.g. hmcts/product-component:branch
   */
  def getBaseShortName() {
    return repositoryName().concat(':')
      .concat(imageTag)
  }

  def getRepositoryName() {
    return repositoryName()
  }

  private def repositoryName() {
    return REPOSITORY.concat('/')
      .concat(this.product).concat('-')
      .concat(this.component)
  }

  private def getRegistryHostname() {
    if (!this.registryHost) {
      this.registryHost = this.acr.getHostname()
    }
    return this.registryHost
  }
}
