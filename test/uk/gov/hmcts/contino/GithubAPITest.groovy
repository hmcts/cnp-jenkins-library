package uk.gov.hmcts.contino

import spock.lang.Specification
import static org.assertj.core.api.Assertions.assertThat

class GithubAPITest extends Specification {

  def steps
  def githubApi

  def labels = ['label1', 'label2']
  def expectedLabels = '["label1","label2"]'

  static def response = ["status": 200, "content": '''[
      {
        "id": 208045946,
        "node_id": "MDU6TGFiZWwyMDgwNDU5NDY=",
        "url": "https://api.github.com/repos/hmcts/some-project/labels/bug",
        "name": "bug",
        "description": "Bug",
        "color": "f29513",
        "default": true
      },
      {
        "id": 208045947,
        "node_id": "MDU6TGFiZWwyMDgwNDU5NDc=",
        "url": "https://api.github.com/repos/hmcts/some-project/labels/enhancement",
        "name": "enhancement",
        "description": "New feature or request",
        "color": "a2eeef",
        "default": false
      }
    ]'''
  ]

  static def responseLabels = ["bug", "enhancement"]

  static def prValuesResponse = ["status": 200, "content": '''[
      {
        "id": 208045946,
        "node_id": "MDU6TGFiZWwyMDgwNDU5NDY=",
        "url": "https://api.github.com/repos/hmcts/some-project/labels/pr-values:ccd",
        "name": "pr-values:ccd",
        "description": "pr-values: ccd label",
        "color": "f29513",
        "default": true
      },
      {
        "id": 208045947,
        "node_id": "MDU6TGFiZWwyMDgwNDU5NDc=",
        "url": "https://api.github.com/repos/hmcts/some-project/labels/random",
        "name": "random",
        "description": "New feature or request",
        "color": "a2eeef",
        "default": false
      },
      {
        "id": 208045947,
        "node_id": "MDU6TGFiZWwyMDgwNDU5NDc=",
        "url": "https://api.github.com/repos/hmcts/some-project/labels/pr-values:xui",
        "name": "pr-values:xui",
        "description": "pr-values:xui",
        "color": "a2eeef",
        "default": false
      }
    ]'''
  ]

  static def prValuesResponseLabels = ["pr-values:ccd", "random", "pr-values:xui"]

  void setup() {
    steps = Mock(JenkinsStepMock.class)
    steps.env >> [CHANGE_URL: "https://github.com/hmcts/some-project/pull/68",
                  CHANGE_ID: "68", GIT_CREDENTIALS_ID:"test-app-id"]
    githubApi = new GithubAPI(steps)
    githubApi.clearLabelCache()
  }

  def "AddLabelsToCurrentPR"() {
    given:
      steps.httpRequest(_) >> response

    when:
      def cache = githubApi.addLabelsToCurrentPR(labels)

    then:
      assertThat(cache).isEqualTo(responseLabels)
  }

  def "AddLabels"() {
    given:
      steps.httpRequest(_) >> response

    when:
      def cache = githubApi.addLabels('evilcorp/my-project', '89', labels)

    then:
      assertThat(cache).isEqualTo(responseLabels)
  }

  // Attempt to check filtering is working on returned labels

  def "getLabelsbyPattern"() {
    given:
      steps.httpRequest(_) >> prValuesResponse

    when:
      def masterLabels = githubApi.getLabelsbyPattern("master", "pr-values")
      def prLabels = githubApi.getLabelsbyPattern("PR-123", "pr-values:ccd")
      def prLabelsNotMatching = githubApi.getLabelsbyPattern("PR-123", "doesntexist")
      def multiplePRLabels = githubApi.getLabelsbyPattern("PR-123", "pr-values")

    then:
      assertThat(masterLabels).isEqualTo([])
      assertThat(prLabelsNotMatching).isEqualTo([])
      assertThat(prLabels).isEqualTo(["pr-values:ccd"])
      assertThat(multiplePRLabels).isEqualTo(["pr-values:ccd","pr-values:xui"])
  }

  def "clearLabelCache"() {
    given:
      // Ensure cache is populated and valid
      steps.httpRequest(_) >> response
      githubApi.addLabelsToCurrentPR(labels)

      // Clear the cache
      githubApi.clearLabelCache()

    when:
      def isValid = githubApi.isCacheValid()
      def isEmpty = githubApi.isCacheEmpty()

    then:
      assertThat(isValid).isFalse()
      assertThat(isEmpty).isTrue()
  }

  def "refreshLabelCache"() {
    given:
      steps.httpRequest(_) >> prValuesResponse

    when:
      def cache = githubApi.refreshLabelCache()

    then:
      assertThat(cache).isEqualTo(prValuesResponseLabels)
  }

  def "CurrentProject"() {
    when:
      def project = githubApi.currentProject()

    then:
      assertThat(project).isEqualTo('hmcts/some-project')
  }

  def "CurrentPullRequestNumber"() {
    when:
      def prNumber = githubApi.currentPullRequestNumber()

    then:
      assertThat(prNumber).isEqualTo('68')
  }

  def "isCacheValid"() {
    given:
      // Ensure cache is populated and valid
      steps.httpRequest(_) >> response
      githubApi.addLabelsToCurrentPR(labels)

    when:
      def validTrue = githubApi.isCacheValid()
      githubApi.clearLabelCache()
      def validFalse = githubApi.isCacheValid()

    then:
      assertThat(validTrue).isTrue()
      assertThat(validFalse).isFalse()
  }

  def "isCacheEmpty"() {
    given:
      // Ensure cache is populated and valid
      steps.httpRequest(_) >> response
      githubApi.addLabelsToCurrentPR(labels)

    when:
      def emptyFalse = githubApi.isCacheEmpty()
      githubApi.clearLabelCache()
      def emptyTrue = githubApi.isCacheEmpty()

    then:
      assertThat(emptyFalse).isFalse()
      assertThat(emptyTrue).isTrue()
  }

  def "getCache"() {
    given:
      // Ensure cache is populated and valid
      steps.httpRequest(_) >> response
      githubApi.addLabelsToCurrentPR(labels)

    when:
      def cache = githubApi.getCache()
      githubApi.clearLabelCache()
      def emptyCache = githubApi.getCache()

    then:
      assertThat(cache).isEqualTo(responseLabels)
      assertThat(emptyCache).isEqualTo([])
  }
}
