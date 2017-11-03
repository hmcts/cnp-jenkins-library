def call(body) {
  // evaluate the body block, and collect configuration into the object
  /*def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config*/

  // now build, based on the configuration provided
  ansiColor('xterm') {
    terraformSetup()

    sh "echo 'Reform Platform Pipeline Initialized...'"

    if (env.GITHUB_PROTOCOL == null)
      env.GITHUB_PROTOCOL = "https"

    withEnv(["GIT_COMMITTER_NAME=jenkinsmoj", "GIT_COMMITTER_EMAIL=jenkinsmoj@contino.io"]) {
      body.call()
    }
  }
}
