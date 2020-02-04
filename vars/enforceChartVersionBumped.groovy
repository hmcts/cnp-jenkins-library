
def call(Map<String, String> params) {
  def product = params.product
  def component = params.component

  writeFile file: 'check-helm-version-bumped.sh', text: libraryResource('uk/gov/hmcts/helm/check-helm-version-bumped.sh')

  sh """
    chmod +x check-helm-version-bumped.sh
    ./check-helm-version-bumped.sh $product $component
  """

  sh 'rm check-helm-version-bumped.sh'
}
