package uk.gov.hmcts.contino

import uk.gov.hmcts.contino.azure.Acr

class DockerImage {

  public static final String TEST_REPO = "test"

  // environment the image has been promoted to
  enum DeploymentStage {
    PR('pr'),
    BUILD('build', "1d", 5),
    STAGING('staging'),
    PREVIEW('preview'),
    PROD('prod', "5d", 5),
    LATEST('latest')

    final String label
    final String purgeAgo
    final String purgeKeep

    private DeploymentStage(String label, String purgeAgo="2h", int keep=3) {
      this.label = label
      this.purgeAgo = purgeAgo
      this.purgeKeep = keep
    }
  }

  def product
  def component
  def imageTag  // image tag, based on the current build branch name e.g. 'latest', 'pr-77'
  def acr
  def commit
  def registryHost
  def lastCommitTime


  DockerImage(product, component, acr, tag, commit, lastCommitTime) {
    this.product = product
    this.component = component
    this.imageTag = tag
    this.acr = acr
    this.commit = commit?.substring(0, 7)
    this.lastCommitTime = lastCommitTime?.trim()
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
   * Get the full image name used by the build. Master builds use a commit-scoped
   * build tag, separate from the staging tag created after checks pass.
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

  def getTag(imageTag) {
    return (imageTag ==  'latest' ? imageTag : "${imageTag}-${this.commit}-${this.lastCommitTime}")
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

  def getShortName(stage) {
    // if it's a PR use the full imageTag (e.g. pr-42)
    if (stage == DeploymentStage.PR) {
      return shortName(this.imageTag)
    }
    return shortName(stage.label)
  }

  private def shortName(imageTag) {
    return repositoryName().concat(':')
      .concat(getTag(imageTag))
  }

  /**
   * Get the build image name without the registry prefix. Master builds include
   * the commit and timestamp so concurrent builds have separate sources.
   * Use this while resolving name for the base image for building or re-tagging.
   *
   * @return
   *   e.g. product/component:build-commit-timestamp or product/component:pr-47
   */
  def getBaseShortName() {
    // Master builds must not overwrite another build's promotion source.
    if (imageTag == 'staging') {
      return shortName(DeploymentStage.BUILD.label)
    }
    return repositoryName().concat(':')
      .concat(imageTag)
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
