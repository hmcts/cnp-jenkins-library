import jenkins.scm.api.SCMSource

// Redirects git@github.com SSH URLs to GitHub App HTTPS auth on this agent,
// so raw git clone/Terraform module fetches don't depend on the outbound SSH key.
// Resolves the same GitHub App credential the job's own SCM source uses (matching checkoutScm),
// falling back to env.GITHUB_APP_CREDENTIAL_ID or a default if that lookup isn't available.
def call() {
  String credId = resolveJobCredentialsId() ?: env.GITHUB_APP_CREDENTIAL_ID ?: 'hmcts-jenkins-cnp'
  try {
    withCredentials([usernamePassword(credentialsId: credId, usernameVariable: 'GH_APP_ID', passwordVariable: 'GH_APP_TOKEN')]) {
      sh '''
        git config --global url."https://x-access-token:${GH_APP_TOKEN}@github.com/".insteadOf "git@github.com:"
        git config --global url."https://x-access-token:${GH_APP_TOKEN}@github.com/".insteadOf "ssh://git@github.com/"
      '''
    }
  } catch (Exception ex) {
    echo "WARNING: Could not configure GitHub App git auth override using credential '${credId}': ${ex.message}"
  }
}

private String resolveJobCredentialsId() {
  try {
    return SCMSource.SourceByItem.findSource(currentBuild.rawBuild.parent)?.credentialsId
  } catch (Exception ex) {
    echo "WARNING: Could not resolve job's SCM credential for GitHub App git auth: ${ex.message}"
    return null
  }
}
