import uk.gov.hmcts.contino.RepositoryUrl

def call(Long deploymentNumber, String url) {
  String repositoryShortUrl = new RepositoryUrl().getShort(env.CHANGE_URL)

  withCredentials([usernamePassword(credentialsId: 'jenkins-github-hmcts-api-token', passwordVariable: 'BEARER_TOKEN', usernameVariable: 'USERNAME')]) {
    httpRequest acceptType: 'APPLICATION_JSON', contentType: 'APPLICATION_JSON',
      customHeaders: [[maskValue: true, name: 'Authorization', value: "Bearer ${env.BEARER_TOKEN}"]], httpMode: 'POST',
      requestBody: """{
        "state": "success",
        "description": "Deployment finished successfully.",
        "environment_url": "${url}"
      }""",
      timeout: 15, url: "https://api.github.com/repos/${repositoryShortUrl}/deployments/${deploymentNumber}/statuses",
      validResponseCodes: '200:201',
      consoleLogResponseBody: true

  }
}
