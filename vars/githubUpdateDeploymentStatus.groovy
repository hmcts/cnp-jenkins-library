import uk.gov.hmcts.contino.RepositoryUrl

def call(Long deploymentNumber, String url) {
  String repositoryShortUrl = new RepositoryUrl().getShort(env.CHANGE_URL)

  withCredentials([usernamePassword(credentialsId: 'jenkins-github-hmcts-api-token', passwordVariable: 'BEARER_TOKEN', usernameVariable: 'USERNAME')]) {
    GString requestBody = """{
      "state": "success",
      "description": "Deployment finished successfully.",
      "environment_url": ${url}
    }"""

    echo requestBody
    echo url

    httpRequest acceptType: 'APPLICATION_JSON', contentType: 'APPLICATION_JSON',
      customHeaders: [[maskValue: true, name: 'Authorization', value: "Bearer ${BEARER_TOKEN}"]], httpMode: 'POST',
      requestBody: """{
        "state": "success",
        "description": "Deployment finished successfully.",
        "environment_url": ${url}
      }""",
      timeout: 15, url: "https://api.github.com/repos/${repositoryShortUrl}/deployments/${deploymentNumber}/statuses",
      validResponseCodes: '200:201'

  }
}
