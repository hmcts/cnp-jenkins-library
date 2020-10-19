import uk.gov.hmcts.contino.RepositoryUrl

def call(Long deploymentNumber, String url) {
  String repositoryShortUrl = new RepositoryUrl().getShort(env.CHANGE_URL)

    httpRequest acceptType: 'APPLICATION_JSON', contentType: 'APPLICATION_JSON',
      authentication: env.GIT_CREDENTIALS_ID,
      httpMode: 'POST',
      requestBody: """{
        "state": "success",
        "description": "Deployment finished successfully.",
        "environment_url": "${url}"
      }""",
      timeout: 15, url: "https://api.github.com/repos/${repositoryShortUrl}/deployments/${deploymentNumber}/statuses",
      validResponseCodes: '200:201',
      consoleLogResponseBody: true
}
