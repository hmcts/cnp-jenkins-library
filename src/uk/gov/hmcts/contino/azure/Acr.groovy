package uk.gov.hmcts.contino.azure

class Acr extends Az {

  def registryName

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
  Acr(steps, subscription, registryName) {
    super(steps, subscription)
    this.registryName = registryName
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
    this.az "acr repository show --name ${registryName} --image ${imageName} --query [digest] -otsv"
  }

}
