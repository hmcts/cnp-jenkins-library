import jenkins.scm.api.SCMSource;

def call() {
  deleteDir()
  def scmVars = checkout scm
  if (scmVars) {
    env.GIT_COMMIT = scmVars.GIT_COMMIT
    env.GIT_URL = scmVars.GIT_URL
  }
  try {
    def credentialsId = SCMSource.SourceByItem.findSource(currentBuild.rawBuild.parent).credentialsId
    env.GIT_CREDENTIALS_ID = credentialsId
    //This code assumes it uses GitHub App Authentication
    def response = steps.httpRequest url: "https://api.github.com/users/$credentialsId%5Bbot%5D", httpMode: 'GET', acceptType: 'APPLICATION_JSON',
      authentication: credentialsId
    def gitUserId = steps.readYaml(text: response.content).id
    env.GIT_APP_EMAIL_ID = gitUserId + "+" + credentialsId + "[bot]@users.noreply.github.com"
  } catch (err) {
    echo "Unable to find git email Id for the user' ${err}'"
  }
}
