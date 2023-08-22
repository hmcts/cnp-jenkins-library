import groovy.json.JsonSlurperClassic
import uk.gov.hmcts.contino.RepositoryUrl

def call(String name = "") {
  String repositoryShortUrl = new RepositoryUrl().getShort(env.CHANGE_URL)

    def response = httpRequest acceptType: 'APPLICATION_JSON', contentType: 'APPLICATION_JSON',
      authentication: env.GIT_CREDENTIALS_ID,
      httpMode: 'POST',
      requestBody: """{
        "ref": "${env.CHANGE_BRANCH}",
        "description": "Deploying ${env.CHANGE_BRANCH}",
        "environment": "preview${name}",
        "required_contexts": [],
        "auto_merge": false,
        "transient_environment": true
      }""",
      timeout: 15, url: "https://api.github.com/repos/${repositoryShortUrl}/deployments", validResponseCodes: '200:201',
      consoleLogResponseBody: true

    def deploymentId = new JsonSlurperClassic().parseText(response.content).id
    return deploymentId
}
