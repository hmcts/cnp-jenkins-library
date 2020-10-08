package uk.gov.hmcts.contino

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

class GithubAPI {

  static final String API_URL = 'https://api.github.com/repos'

  def steps

  GithubAPI(steps) {
    this.steps = steps
  }

  /**
   * Add labels to the current pull request.  MUST be run with an onPR() closure.
   *
   * @param labels
   *   A List of labels
   */
  def addLabelsToCurrentPR(labels) {

    def project = currentProject()
    def pullRequestNumber = currentPullRequestNumber()

    addLabels(project, pullRequestNumber, labels)
  }

  /**
   * Add labels to an issue or pull request
   *
   * @param project
   *   The project repo name, including the org e.g. 'hmcts/my-frontend-app'
   * @param issueNumber
   *   The issue or PR number
   * @param labels
   *   A List of labels
   */
  def addLabels(project, issueNumber, labels) {

    def body = JsonOutput.toJson(labels)

    def response = this.steps.httpRequest(httpMode: 'POST',
      authentication: this.steps.env.GIT_CREDENTIALS_ID,
      acceptType: 'APPLICATION_JSON',
      contentType: 'APPLICATION_JSON',
      url: "${API_URL}/${project}/issues/${issueNumber}/labels",
      requestBody: "${body}",
      consoleLogResponseBody: true,
      validResponseCodes: '200')
  }

  /**
   * Check Pull Request for dependencies label.
   */
  def checkForDependenciesLabel(branch_name) {

    if (new ProjectBranch(branch_name).isPR() == true) {
      def project = currentProject()
      def pullRequestNumber = currentPullRequestNumber()

      return getLabels(project, pullRequestNumber).contains("dependencies")
    } else {
      return false
    }
  }

  /**
   * Get labels from an issue or pull request
   *
   * @param project
   *   The project repo name, including the org e.g. 'hmcts/my-frontend-app'
   * @param issueNumber
   *   The issue or PR number
   */
  def getLabels(project, issueNumber) {

    def response = this.steps.httpRequest(httpMode: 'GET',
      authentication: this.steps.env.GIT_CREDENTIALS_ID,
      acceptType: 'APPLICATION_JSON',
      contentType: 'APPLICATION_JSON',
      url: "${API_URL}/${project}/issues/${issueNumber}/labels",
      consoleLogResponseBody: true,
      validResponseCodes: '200')

    def json_response = new JsonSlurper().parseText(response.content)

    return json_response.stream().map( { label -> label['name'] } ).collect()
  }

  def currentProject() {
    return new RepositoryUrl().getShort(this.steps.env.CHANGE_URL)
  }

  def currentPullRequestNumber() {
    return this.steps.env.CHANGE_ID
  }
}
