import groovy.json.JsonSlurperClassic
import uk.gov.hmcts.contino.RepositoryUrl
import uk.gov.hmcts.contino.RateLimitedGithub

def call(String suffix = "") {
  String repositoryShortUrl = new RepositoryUrl().getShort(env.CHANGE_URL)

    def response = new RateLimitedGithub(this).postWithDistributedCredentials(env.GIT_CREDENTIALS_ID, [
      acceptType: 'APPLICATION_JSON', contentType: 'APPLICATION_JSON',
      requestBody: """{
        "ref": "${env.CHANGE_BRANCH}",
        "description": "Deploying ${env.CHANGE_BRANCH}",
        "environment": "preview${suffix}",
        "required_contexts": [],
        "auto_merge": false,
        "transient_environment": true
      }""",
      timeout: 15, url: "https://api.github.com/repos/${repositoryShortUrl}/deployments", validResponseCodes: '200:201',
      consoleLogResponseBody: true
    ])

    def deploymentId = null
    if (response.content) {
      deploymentId = new JsonSlurperClassic().parseText(response.content).id
    }
    return deploymentId
}
