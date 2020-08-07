package uk.gov.hmcts.contino

import spock.lang.Specification
import static org.assertj.core.api.Assertions.assertThat

class GithubAPITest extends Specification {

  def steps
  def githubApi

  def labels = ['label1', 'label2']
  def expectedLabels = '["label1","label2"]'

  void setup() {
    steps = Mock(JenkinsStepMock.class)
    steps.env >> [CHANGE_URL: "https://github.com/hmcts/some-project/pull/68",
                  CHANGE_ID: "68", GIT_CREDENTIALS_ID:"test-app-id"]
    githubApi = new GithubAPI(steps)
  }

  def "AddLabelsToCurrentPR"() {

    def expectedUrl = 'https://api.github.com/repos/hmcts/some-project/issues/68/labels'

    when:
      githubApi.addLabelsToCurrentPR(labels)

    then:
      1 * steps.httpRequest({it.get('httpMode').equals('POST') &&
                             it.get('authentication').equals("test-app-id") &&
                             it.get('acceptType').equals('APPLICATION_JSON') &&
                             it.get('contentType').equals('APPLICATION_JSON') &&
                             it.get('url').equals("${expectedUrl}") &&
                             it.get('requestBody').equals("${expectedLabels}") &&
                             it.get('consoleLogResponseBody').equals(true) &&
                             it.get('validResponseCodes').equals('200')})
  }

  def "AddLabels"() {

    def expectedUrl = 'https://api.github.com/repos/evilcorp/my-project/issues/89/labels'

    when:
      githubApi.addLabels('evilcorp/my-project', '89', labels)

    then:
      1 * steps.httpRequest({it.get('httpMode').equals('POST') &&
        it.get('authentication').equals("test-app-id") &&
        it.get('acceptType').equals('APPLICATION_JSON') &&
        it.get('contentType').equals('APPLICATION_JSON') &&
        it.get('url').equals("${expectedUrl}") &&
        it.get('requestBody').equals("${expectedLabels}") &&
        it.get('consoleLogResponseBody').equals(true) &&
        it.get('validResponseCodes').equals('200')})
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
}
