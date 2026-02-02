def call(Closure block) {
  String credentialsId = (env.FORTIFY_OAUTH_CREDENTIALS_ID ?: 'fortify-on-demand-oauth').toString().trim()

  if (!credentialsId) {
    echo('Fortify: missing FORTIFY_OAUTH_CREDENTIALS_ID; proceeding without Fortify OAuth credentials')
    block.call()
    return
  }

  try {
    withCredentials([usernamePassword(credentialsId: credentialsId, usernameVariable: 'FORTIFY_OAUTH_CLIENT_ID', passwordVariable: 'FORTIFY_OAUTH_CLIENT_SECRET')]) {
      block.call()
    }
  } catch (Exception e) {
    echo("Fortify: unable to bind Jenkins credentialsId='${credentialsId}'; proceeding without Fortify OAuth credentials (${e.message})")
    block.call()
  }
}
