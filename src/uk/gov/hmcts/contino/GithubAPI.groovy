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
    this.steps.echo "Adding the following labels: ${labels}"
    def body = JsonOutput.toJson(labels)
    this.steps.echo "Request Body: ${body}"
    def response = this.steps.httpRequest(httpMode: 'POST',
      authentication: this.steps.env.GIT_CREDENTIALS_ID,
      acceptType: 'APPLICATION_JSON',
      contentType: 'APPLICATION_JSON',
      url: "${API_URL}/${project}/issues/${issueNumber}/labels",
      requestBody: "${body}",
      consoleLogResponseBody: true,
      validResponseCodes: '200')


    def statusCode = response.status
    this.steps.echo "Response Status Code: ${statusCode}"
//    if (statusCode == 200) {
//      this.steps.echo "Response Ok."
//      if (this.cachedLabelList.isValid) {
//        this.steps.echo "Cache is Valid.  Adding new labels to cache."
//        this.cachedLabelList.cache.addAll(labels)
//        this.steps.echo "Updated Cache Contents: ${this.cachedLabelList.cache}"
//      } else {
//        this.steps.echo "Cache is Invalid.  Refreshing Cache."
//        this.refreshLabelCache()
//      }
//    } else {
//      this.steps.echo "Failed to Add Labels."
//    }
  }

  /**
   * Clears this.cachedLabelList
   */
  private void clearLabelCache() {
    this.steps.echo "Clearing Label Cache."
    this.cachedLabelList.cache = []
    this.cachedLabelList.isValid = false
    this.steps.echo "Cleared Cache Contents: ${this.cachedLabelList.cache}"
    this.steps.echo "Cleared Cache Valid?: ${this.cachedLabelList.isValid}"
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
      url: "${API_URL}/${project}/issues/${issueNumber}/labels",
      consoleLogResponseBody: true,
      validResponseCodes: '200')

    def json_response = new JsonSlurper().parseText(response.content)
    this.cachedLabelList.cache = json_response.collect( { label -> label['name'] } )
    this.cachedLabelList.isValid = true
    this.steps.echo "Cache Contents: ${this.cachedLabelList.cache}"
    this.steps.echo "Cache Valid?: ${this.cachedLabelList.isValid}"
    return this.cachedLabelList.cache
  }

  /**
   * Check this.cachedLabelList, if empty, call getLabels() to repopulate.
   */
  private getLabelsFromCache() {
    this.steps.echo "Getting Labels From Cache"
    this.steps.echo "Cache Valid?: ${this.cachedLabelList.isValid}"
    if (!this.cachedLabelList.isValid) {
      this.steps.echo "Cache Invalid.  Calling Refresh."
      return refreshLabelCache()
    }

    this.steps.echo "Cache Empty?: ${this.cachedLabelList.cache.isEmpty()}"
    if (this.cachedLabelList.cache.isEmpty() && this.cachedLabelList.isValid) {
      this.steps.echo "Cache is Empty and Valid.  Returning Empty List."
      return []
    }

    this.steps.echo "Cache is Valid.  Returning Cache Content: ${this.cachedLabelList.cache}"
    return this.cachedLabelList.cache
  }

  /**
   * Check Pull Request for label by a pattern in name.
   */
  def getLabelsbyPattern(String branch_name, String key) {
    this.steps.echo "Getting Labels for Branch: ${branch_name} by Pattern: ${key}"
    def isPR = new ProjectBranch(branch_name).isPR()
    this.steps.echo "Is this a PR?: ${isPR}"
    if (isPR) {
      this.steps.echo "PR Confirmed.  Calling getLabels()."
      def foundLabels = getLabels().findAll{it.contains(key)}
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

  /**
   * Get all labels from an issue or pull request
   */
  def getLabels() {
    this.steps.echo "Getting All Labels.  Calling getLabelsFromCache()."
    return this.getLabelsFromCache()
  }

  def currentProject() {
    return new RepositoryUrl().getShort(this.steps.env.CHANGE_URL)
  }

  def currentPullRequestNumber() {
    return this.steps.env.CHANGE_ID
  }
}
