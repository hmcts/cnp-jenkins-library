def call() {
  deleteDir()
  def scmVars = checkout scm
  if (scmVars) {
    env.GIT_COMMIT = scmVars.GIT_COMMIT
    env.GIT_URL = scmVars.GIT_URL
  }
}
