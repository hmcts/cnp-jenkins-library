package uk.gov.hmcts.contino

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

class GithubAPI {

  static final String API_URL = 'https://api.github.com/repos'

  def steps

  GithubAPI(steps) {
    this.steps = steps
  }

  private static cachedLabelList = [
    'isValid': false,
    'cache': []
  ]

  def currentProject() {
    return new RepositoryUrl().getShort(this.steps.env.CHANGE_URL)
  }

  def currentPullRequestNumber() {
    return this.steps.env.CHANGE_ID
  }

  /**
   * Return whether the cache is valid
   */
  def static isCacheValid() {
    return cachedLabelList.isValid
  }

  /**
   * Return whether the cache is empty
   */
  def static isCacheEmpty() {
    return cachedLabelList.cache.isEmpty()
  }

  /**
   * Return cache contents as list
   */
  def static getCache() {
    return cachedLabelList.cache
  }

  /**
   * Clears this.cachedLabelList
   */
  void clearLabelCache() {
    cachedLabelList.cache = []
    cachedLabelList.isValid = false
    this.steps.echo "Cleared and invalidated label cache."
  }

  /**
   * Refreshes this.cachedLabelList
   */
  def refreshLabelCache() {
    this.steps.echo "Refreshing label cache."
    def project = currentProject()
    def issueNumber = currentPullRequestNumber()
    def response = this.steps.httpRequest(httpMode: 'GET',
      authentication: this.steps.env.GIT_CREDENTIALS_ID,
      acceptType: 'APPLICATION_JSON',
      contentType: 'APPLICATION_JSON',
      url: API_URL + "/${project}/issues/${issueNumber}/labels",
      consoleLogResponseBody: true,
      validResponseCodes: '200')

    if (response.status == 200) {
      def json_response = new JsonSlurper().parseText(response.content)
      cachedLabelList.cache = json_response.collect({ label -> label['name'] })
      cachedLabelList.isValid = true
      this.steps.echo "Updated cache contents: ${getCache()}"
    } else {
      this.steps.echo "Failed to update cache. Server returned status: ${response.status}"
    }

    return getCache()
  }

  /**
   * Add labels to an issue or pull request
   * If the API call is successful, valid cache will be directly updated whilst Invalid cache will be refreshed.
   *
   * @param project
   *   The project repo name, including the org e.g. 'hmcts/my-frontend-app'
   * @param issueNumber
   *   The issue or PR number
   * @param labels
   *   A List of labels
   */
  def addLabels(project, issueNumber, labels) {
    this.steps.echo "Adding the following labels: ${labels}"
    def body = JsonOutput.toJson(labels)
    def response = this.steps.httpRequest(httpMode: 'POST',
      authentication: this.steps.env.GIT_CREDENTIALS_ID,
      acceptType: 'APPLICATION_JSON',
      contentType: 'APPLICATION_JSON',
      url: API_URL + "/${project}/issues/${issueNumber}/labels",
      requestBody: "${body}",
      consoleLogResponseBody: true,
      validResponseCodes: '200')

    if (response.status == 200) {
      if (isCacheValid()) {
        cachedLabelList.cache.addAll(labels)
        cachedLabelList.cache.unique()
        this.steps.echo "Cache is valid. Updated cache contents: ${getCache()}"
      } else {
        this.steps.echo "Cache is invalid. Calling refresh."
        return this.refreshLabelCache()
      }
    } else {
      this.steps.echo "Failed to add labels. Server returned status: ${response.status}"
    }

    return getCache()
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
    return addLabels(project, pullRequestNumber, labels)
  }

  /**
   * Check this.cachedLabelList, if empty, call getLabels() to repopulate.
   */
  private getLabelsFromCache() {
    if (!isCacheValid()) {
      return refreshLabelCache()
    }

    if (isCacheEmpty() && isCacheValid()) {
      return []
    }

    return getCache()
  }

  /**
   * Get all labels from an issue or pull request
   */
  def getLabels(String branchName) {
    if (new ProjectBranch(branchName).isPR()) {
      return this.getLabelsFromCache()
    } else {
      return []
    }
  }

  /**
   * Check Pull Request for label by a pattern in name.
   */
  def getLabelsbyPattern(String branchName, String key) {
    return getLabels(branchName).findAll{it.contains(key)}
  }

  /**
   * Check Pull Request for specified label.
   */
  def checkForLabel(String branchName, String key) {
    return getLabels(branchName).contains(key)
  }

  /**
   * Check Pull Request for dependencies label.
   */
  def checkForDependenciesLabel(branchName) {
    return checkForLabel(branchName, "dependencies")
  }
}
