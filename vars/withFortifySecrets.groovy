def call(String fortifyVaultName = null, Closure block) {
  String credentialsId = (env.FORTIFY_CREDENTIALS_ID ?: 'fortify-on-demand-oauth').toString().trim()

  if (!credentialsId) {
    echo('Fortify: missing FORTIFY_CREDENTIALS_ID; proceeding without Fortify credentials')
    block.call()
    return
  }

  try {
    withCredentials([usernamePassword(credentialsId: credentialsId, usernameVariable: 'FORTIFY_USER_NAME', passwordVariable: 'FORTIFY_PASSWORD')]) {
      block.call()
    }
  } catch (Exception e) {
    echo("Fortify: unable to bind Jenkins credentialsId='${credentialsId}'; proceeding without Fortify credentials (${e.message})")
    block.call()
  }
}
