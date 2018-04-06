import groovy.json.JsonSlurperClassic
import uk.gov.hmcts.contino.RepositoryUrl

def call() {
  String repositoryShortUrl = new RepositoryUrl().getShort(env.CHANGE_URL)

  withCredentials([usernamePassword(credentialsId: 'jenkins-github-hmcts-api-token', passwordVariable: 'BEARER_TOKEN', usernameVariable: 'USERNAME')]) {
    def response = httpRequest consoleLogResponseBody: true, acceptType: 'APPLICATION_JSON', contentType: 'APPLICATION_JSON',
      customHeaders: [[maskValue: true, name: 'Authorization', value: "Bearer ${BEARER_TOKEN}"]], httpMode: 'POST',
      requestBody: """{
        "ref": "${env.CHANGE_BRANCH}",
        "description": "Deploying ${env.CHANGE_BRANCH}",
        "environment": "preview",
        "required_contexts": [],
        "auto_merge": false,
        "transient_environment": true
      }""",
      timeout: 15, url: "https://api.github.com/repos/${repositoryShortUrl}/deployments", validResponseCodes: '200:201'

    def deploymentId = new JsonSlurperClassic().parseText(response.content).id
    return deploymentId
  }
}
