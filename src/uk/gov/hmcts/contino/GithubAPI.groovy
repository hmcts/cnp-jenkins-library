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
   * Check for Pull Request
   */
  private isPR(String branch_name) {
    def isPr = new ProjectBranch(branch_name).isPR()
    this.steps.echo "Is ${branch_name} a PR?: ${isPr}"
    return isPr
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
    this.steps.echo "Clearing Label Cache."
    cachedLabelList.cache = []
    cachedLabelList.isValid = false
    this.steps.echo "Cleared Cache Contents: ${getCache()}"
    this.steps.echo "Cleared Cache Valid?: ${isCacheValid()}"
  }

  /**
   * Refreshes this.cachedLabelList
   */
  def refreshLabelCache() {
    this.steps.echo "Refreshing Label Cache"
    def project = currentProject()
    def issueNumber = currentPullRequestNumber()
    def response = this.steps.httpRequest(httpMode: 'GET',
      authentication: this.steps.env.GIT_CREDENTIALS_ID,
      acceptType: 'APPLICATION_JSON',
      contentType: 'APPLICATION_JSON',
      url: API_URL + "/${project}/issues/${issueNumber}/labels",
      consoleLogResponseBody: true,
      validResponseCodes: '200')

    this.steps.echo "Response Status Code: ${response.status}"

    if (response.status == 200) {
      this.steps.echo "Response Ok."
      def json_response = new JsonSlurper().parseText(response.content)
      cachedLabelList.cache = json_response.collect({ label -> label['name'] })
      cachedLabelList.isValid = true
      this.steps.echo "Cache Contents: ${getCache()}"
      this.steps.echo "Cache Valid?: ${isCacheValid()}"
    } else {
      this.steps.echo "Failed to Update cache.  Server returned ${response.status} response."
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
    this.steps.echo "Request Body: ${body}"
    def response = this.steps.httpRequest(httpMode: 'POST',
      authentication: this.steps.env.GIT_CREDENTIALS_ID,
      acceptType: 'APPLICATION_JSON',
      contentType: 'APPLICATION_JSON',
      url: API_URL + "/${project}/issues/${issueNumber}/labels",
      requestBody: "${body}",
      consoleLogResponseBody: true,
      validResponseCodes: '200')

    this.steps.echo "Response Status Code: ${response.status}"

    if (response.status == 200) {
      this.steps.echo "Response Ok."
      if (isCacheValid()) {
        this.steps.echo "Cache is Valid.  Adding new labels to cache."
        cachedLabelList.cache.addAll(labels)
        cachedLabelList.cache.unique()
        this.steps.echo "Updated Cache Contents: ${getCache()}"
      } else {
        this.steps.echo "Cache is Invalid.  Refreshing Cache."
        return this.refreshLabelCache()
      }
    } else {
      this.steps.echo "Failed to Add Labels.  Server returned ${response.status} response."
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
    this.steps.echo "Adding Labels to current PR (${project} / ${pullRequestNumber})"
    return addLabels(project, pullRequestNumber, labels)
  }

  /**
   * Check this.cachedLabelList, if empty, call getLabels() to repopulate.
   */
  private getLabelsFromCache() {
    this.steps.echo "Getting Labels From Cache"
    this.steps.echo "Cache Valid?: ${isCacheValid()}"
    if (!isCacheValid()) {
      this.steps.echo "Cache Invalid.  Calling Refresh."
      return refreshLabelCache()
    }

    this.steps.echo "Cache Empty?: ${isCacheEmpty()}"
    if (isCacheEmpty() && isCacheValid()) {
      this.steps.echo "Cache is Empty and Valid.  Returning Empty List."
      return []
    }

    this.steps.echo "Cache is Valid.  Returning Cache Content: ${getCache()}"
    return getCache()
  }

  /**
   * Get all labels from an issue or pull request
   */
  def getLabels(String branch_name) {
    this.steps.echo "Getting All Labels for ${branch_name}."
    if (isPR(branch_name)) {
      this.steps.echo "PR Confirmed.  Calling getLabelsFromCache()."
      return this.getLabelsFromCache()
    } else {
      this.steps.echo "Negative PR.  Returning Empty List."
      return []
    }
  }

  /**
   * Check Pull Request for label by a pattern in name.
   */
  def getLabelsbyPattern(String branch_name, String key) {
    this.steps.echo "Getting Labels for Branch: ${branch_name} by Pattern: ${key}"
    if (isPR(branch_name)) {
      this.steps.echo "PR Confirmed.  Calling getLabels()."
      def foundLabels = getLabels(branch_name).findAll{it.contains(key)}
      this.steps.echo "Returning Labels: ${foundLabels}"
      return foundLabels
    } else {
      this.steps.echo "Negative PR.  Returning Empty List."
      return []
    }
  }

  /**
   * Check Pull Request for dependencies label.
   */
  def checkForDependenciesLabel(branch_name) {
    this.steps.echo "Checking for Dependencies Label by calling getLabelsbyPattern()."
    def depLabel = getLabelsbyPattern(branch_name, "dependencies").contains("dependencies")
    this.steps.echo "Found Dependencies Label?: ${depLabel}"
    return depLabel
  }
}
