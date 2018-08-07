package uk.gov.hmcts.contino.azure

class Acr extends Az {

  Acr(steps, subscription) {
    super(steps, subscription)
  }

  /**
   * Log into ACR.  Can be used instead of 'docker login'.  You need to be logged into a subscription first.
   *
   * @param registryName
   *   the 'resource name' of the ACR registry.  i.e. 'cnpacr' not 'cnpacr.azurecr.io'
   *
   * @return
   *   stdout/stderr of login command
   */
  def login(registryName) {
    this.az "acr login --name ${registryName}"
  }

  /**
   * Gets the registry digest of a given image
   *
   * @param registryName
   *   the 'resource name' of the ACR registry.  i.e. 'cnpacr' not 'cnpacr.azurecr.io'
   *
   * @param imageName
   *   the image name, including repository, image name and tag.  e.g. 'hmcts/alpine-test:sometag`
   *
   * @return
   *   The raw value of the digest e.g. sha256:c8aa9687b927cb65ced1aa7bd7756c2af5e84a79b54dd67cb91177d9071396aa
   */
  def getImageDigest(registryName, imageName) {
    this.az "acr repository show --name ${registryName} --image ${imageName} --query [digest] -otsv"
  }

}
