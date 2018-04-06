def call(Long deploymentNumber, String url) {
  withCredentials([usernamePassword(credentialsId: 'jenkins-github-hmcts-api-token', passwordVariable: 'BEARER_TOKEN', usernameVariable: 'USERNAME')]) {
    httpRequest acceptType: 'APPLICATION_JSON', contentType: 'APPLICATION_JSON',
      customHeaders: [[maskValue: true, name: 'Authorization', value: "Bearer ${BEARER_TOKEN}"]], httpMode: 'POST',
      requestBody: """{
        "state": "success",
        "description": "Deployment finished successfully.",
        "environment_url": ${url}
      }""",
      timeout: 15, url: "https://api.github.com/repos/hmcts/cmc-claim-store/deployments/${deploymentNumber}/statuses",
      validResponseCodes: '200:201'

  }
}
