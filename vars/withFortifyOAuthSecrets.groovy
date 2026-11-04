def call(Closure block) {
  String credentialsId = (env.FORTIFY_OAUTH_CREDENTIALS_ID ?: 'fortify-on-demand-oauth').toString().trim()

  if (!credentialsId) {
    echo('Fortify: missing FORTIFY_OAUTH_CREDENTIALS_ID; proceeding without Fortify OAuth credentials')
    block.call()
    return
  }

  // Only a credential binding failure is tolerated; block errors and aborts propagate.
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
