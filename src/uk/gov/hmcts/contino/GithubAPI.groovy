package uk.gov.hmcts.contino

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

class GithubAPI {

  static final String API_URL = 'https://api.github.com/repos'

  def steps

  GithubAPI(steps) {
    this.steps = steps
  }

  private cachedLabelList = []

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
   * Empties this.cachedLabelList
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

    this.cachedLabelList = []
  }

/**
 * Refreshes this.cachedLabelList
*/
  def refreshLabelCache() {
    def project = currentProject()
    def issueNumber = currentPullRequestNumber()
    def response = this.steps.httpRequest(httpMode: 'GET',
      authentication: this.steps.env.GIT_CREDENTIALS_ID,
      acceptType: 'APPLICATION_JSON',
      contentType: 'APPLICATION_JSON',
      url: "${API_URL}/${project}/issues/${issueNumber}/labels",
      consoleLogResponseBody: true,
      validResponseCodes: '200')

    def json_response = new JsonSlurper().parseText(response.content)
    this.cachedLabelList = json_response.collect( { label -> label['name'] } )
    return this.cachedLabelList
  }

/**
 * Check this.cachedLabelList, if empty, call getLabels() to repopulate.
*/
  private getLabelsFromCache() {
    if (this.cachedLabelList.size() == 0) {
      return refreshLabelCache()
    }

    return this.cachedLabelList
  }


/**
 * Check Pull Request for label by a pattern in name.
 */
  def getLabelsbyPattern(String branch_name, String key) {
    if (new ProjectBranch(branch_name).isPR() == true) {
      return getLabels().findAll{it.contains(key)}
    } else {
      return []
    }
  }
/**
 * Check Pull Request for dependencies label.
 */
  def checkForDependenciesLabel(branch_name) {
      return getLabelsbyPattern(branch_name, "dependencies").contains("dependencies")
  }

  /**
   * Get all labels from an issue or pull request
   */
  def getLabels() {
    return this.getLabelsFromCache()
  }

  def currentProject() {
    return new RepositoryUrl().getShort(this.steps.env.CHANGE_URL)
  }

  def currentPullRequestNumber() {
    return this.steps.env.CHANGE_ID
  }
}
