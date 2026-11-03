def call(Closure block) {
  String credentialsId = (env.FORTIFY_OAUTH_CREDENTIALS_ID ?: 'fortify-on-demand-oauth').toString().trim()

  if (!credentialsId) {
    echo('Fortify: missing FORTIFY_OAUTH_CREDENTIALS_ID; proceeding without Fortify OAuth credentials')
    block.call()
    return
  }

  // Only a failure to bind the credential is tolerated. Once the block has started, its own errors
  // propagate unchanged (catching them here used to run the whole scan a second time), and an
  // abort/timeout is never downgraded.
  boolean credentialsBound = false
  try {
    withCredentials([usernamePassword(credentialsId: credentialsId, usernameVariable: 'FORTIFY_OAUTH_CLIENT_ID', passwordVariable: 'FORTIFY_OAUTH_CLIENT_SECRET')]) {
      credentialsBound = true
      block.call()
    }
  } catch (Exception e) {
    if (credentialsBound || e instanceof InterruptedException) {
      throw e
    }
    echo("Fortify: unable to bind Jenkins credentialsId='${credentialsId}'; proceeding without Fortify OAuth credentials (${e.message})")
    block.call()
  }
}
