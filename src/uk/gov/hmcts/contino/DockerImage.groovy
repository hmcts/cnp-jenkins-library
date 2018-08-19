package uk.gov.hmcts.contino

import uk.gov.hmcts.contino.azure.Acr

class DockerImage {

  static String REPOSITORY = 'hmcts'

  def product
  def component
  def imageTag
  Acr acr
  def registryHost

  DockerImage(product, component, acr, tag) {
    this.product = product
    this.component = component
    this.imageTag = tag
    this.acr = acr
  }

  /**
   * Get the full image name, including the tag. Use when you
   * need to build an image
   *
   * @return
   *   the image name, e.g.: 'cnpacr.azurecr.io/hmcts/alpine-test:sometag'
   */
  def getTaggedName() {
    return this.getRegistryHostname().concat('/')
      .concat(this.getShortName())
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
   * Get the image tag, based on the current build branch name
   *
   * @return
   *   the tag e.g. 'latest', 'pr-77'
   */
  def getTag() {
    return this.imageTag
  }

  /**
   * Get the 'short name' of the image, without the registry prefix
   *
   * @return
   *   the short name. e.g. hmcts/product-component:branch
   */
  def getShortName() {
    return REPOSITORY.concat('/')
      .concat(this.product).concat('-')
      .concat(this.component).concat(':')
      .concat(this.imageTag)
  }

  private def getRegistryHostname() {
    if (!this.registryHost) {
      this.registryHost = this.acr.getHostname()
    }
    return this.registryHost
  }
}
