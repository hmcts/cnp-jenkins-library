package uk.gov.hmcts.contino

import spock.lang.Specification
import uk.gov.hmcts.ardoq.ArdoqClient
import static org.assertj.core.api.Assertions.assertThat

class ArdoqClientTest extends Specification {
  public steps
  def ardoqClient

  void setup() {
    steps = Mock(JenkinsStepMock.class)
    steps.env >> [ARDOQ_APPLICATION_ID: "FOOBAR_APP",
                  "GIT_URL": "https://github.com/hmcts/some-project"]
    ardoqClient = new ArdoqClient("sdfhgf", "https://hmcts.ardoq.local/", steps)
  }

  def "ApplicationId"() {
    when:
    def appId = ardoqClient.getApplicationId()

    then:
    assertThat(appId).isEqualTo('FOOBAR_APP')
  }

  def "RepositoryName"() {
    when:
    def repoName = ardoqClient.getRepositoryName()

    then:
    assertThat(repoName).isEqualTo('some-project')
  }

  def "Language"() {
    when:
    ardoqClient.getLanguage()

    then:
    1 * steps.echo('No Dockerfile found, skipping tech stack maintenance')

  }

  def "LanguageVersion"() {
    when:
    ardoqClient.getLanguageVersion()

    then:
    1 * steps.echo('No Dockerfile found, skipping tech stack maintenance')
  }

  def "UpdateDependencies"() {
    when:
    ardoqClient.updateDependencies("deps", "yarn")

    then:
    2 * steps.echo('No Dockerfile found, skipping tech stack maintenance')
    1 * steps.echo('Missing required parameters for tech stack maintenance')
  }

  def "json"() {
    when:
    String json = ArdoqClient.getJson("appId", "repoName", "deps", "yarn", "java", "1.8")

    then:
    assertThat(json).isEqualTo("""\
              {
              "vcsHost": "Github HMCTS",
              "hmctsApplication": "appId",
              "codeRepository": "repoName",
              "encodedDependencyList": "deps",
              "parser": "yarn",
              "language": "java",
              "languageVersion": "1.8"
              }
              """.stripIndent())
  }
}


