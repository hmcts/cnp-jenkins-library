def call(Map<String, String> params) {
  def product = params.product
  def component = params.component
  def branch = env.CHANGE_BRANCH
  def credentialsId = env.GIT_CREDENTIALS_ID
  def gitEmailId = env.GIT_APP_EMAIL_ID

  writeFile file: 'check-helm-version-bumped.sh', text: libraryResource('uk/gov/hmcts/helm/check-helm-version-bumped.sh')

  withCredentials([usernamePassword(credentialsId: credentialsId, passwordVariable: 'BEARER_TOKEN', usernameVariable: 'APP_ID')]) {
    def bearerToken = env.BEARER_TOKEN
    sh """
    chmod +x check-helm-version-bumped.sh
    ./check-helm-version-bumped.sh $product $component $branch $credentialsId $bearerToken $gitEmailId
  """
  }

  sh 'rm check-helm-version-bumped.sh'
}
