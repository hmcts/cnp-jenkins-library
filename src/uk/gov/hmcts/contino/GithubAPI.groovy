package uk.gov.hmcts.contino

import groovy.json.JsonOutput

class GithubAPI {

  private static final String API_URL = "https://api.github.com/repos/"

  def steps

  GithubAPI(steps) {
    this.steps = steps
  }

  def addLabels(project, issueNumber, labels) {

    def body = JsonOutput.toJson(labels)

    def response = this.steps.httpRequest(httpMode: 'POST',
      authentication: 'jenkins-github-hmcts-api-token',
      acceptType: 'APPLICATION_JSON',
      contentType: 'APPLICATION_JSON',
      url: "${API_URL}/${project}/issues/${issueNumber}/labels",
      requestBody: "${body}",
      consoleLogResponseBody: true,
      validResponseCodes: '200')
  }
}
