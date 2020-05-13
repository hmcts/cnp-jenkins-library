package uk.gov.hmcts.pipeline

import spock.lang.Specification
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.JenkinsStepMock

import static org.assertj.core.api.Assertions.assertThat

class TeamConfigTest extends Specification {

  def steps
  def teamConfig
  def slackContactTeamConfig
  static def response = ["content": ["cmc":["team":"Money Claims","namespace":"money-claims","slack": ["contact_channel":"#cmc-builds", "build_notices_channel":"#cmc-builds" ]],
                                     "bar":["team":"Fees/Pay","namespace":"fees-pay","slack": ["contact_channel":"#fees-builds", "build_notices_channel":"#fees-builds" ]],
                                     "ccd":["namespace":"ccd", "slack": ["contact_channel":"#ccd-builds", "build_notices_channel":"#ccd-builds" ]],
                                     "dm":["team":"CCD","slack": ["contact_channel":"", "build_notices_channel":"" ]],
                                     "product":["namespace":"product", "team":"hmcts","slack": ["contact_channel":"#product-builds", "build_notices_channel":"#product-builds" ]],
                                     "bulk-scan":["team":"Software Engineering","namespace":"rpe","slack": ["contact_channel":"#rpe-builds", "build_notices_channel":"#rpe-builds" ]]]]

  void setup() {
    steps = Mock(JenkinsStepMock.class)
    steps.readYaml([text: response.content]) >> response.content
    steps.httpRequest(_) >> response
    steps.error(_) >> { throw new Exception(_ as String) }
    teamConfig = new TeamConfig(steps)
    slackContactTeamConfig = new TeamConfig(steps)
  }

  def "getName() with product name starting pr- should return correct teamname"() {
    def productName = 'pr-12-bar'
    def expected = 'Fees/Pay'
    when:
    def teamName = teamConfig.getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }

  def "getName() with non existing product name starting pr- should return default teamname"() {
    def productName = 'pr-12-nonexisting'
    def expected = TeamConfig.DEFAULT_TEAM_NAME
    when:
    def teamName = teamConfig.getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }

  def "getName() with valid product name should return correct team name"() {
    def productName = 'bulk-scan'
    def expected = 'Software Engineering'

    when:
    def teamName = teamConfig.getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }

  def "getName() with valid product name without name should return default team name"() {
    def productName = 'ccd'
    def expected = TeamConfig.DEFAULT_TEAM_NAME

    when:
    def teamName = teamConfig.getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }

  def "getName() with non existing product name should return default teamname"() {
    def productName = 'idontexist'
    def expected = TeamConfig.DEFAULT_TEAM_NAME

    when:
    def teamName = teamConfig.getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }

  def "getNameSpace() with product name starting pr- should return correct namespace"() {
    def productName = 'pr-12-bar'
    def expected = 'fees-pay'
    when:
    def teamName = teamConfig.getNameSpace(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }

  def "getNameSpace() with non existing product name starting pr- should throw exception"() {
    def productName = 'pr-12-nonexisting'
    def expected = TeamConfig.DEFAULT_TEAM_NAME
    when:
    def teamName = teamConfig.getNameSpace(productName)

    then:
    thrown RuntimeException
  }

  def "getNameSpace() with valid product name should return correct team namespace"() {
    def productName = 'bulk-scan'
    def expected = 'rpe'

    when:
    def teamName = teamConfig.getNameSpace(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }

  def "getNameSpace() with valid product name without namespace should throw error"() {
    def productName = 'dm'

    when:
    teamConfig.getNameSpace(productName)

    then:
    thrown RuntimeException
  }

  def "getNameSpace() with non existing product name should throw error"() {
    def productName = 'idontexist'

    when:
    def teamName = teamConfig.getNameSpace(productName)

    then:
    thrown RuntimeException
  }

  def "getContactSlackChannel() with empty value should return mapping from team config"() {

    when:
    def slackChannel = teamConfig.getContactSlackChannel('cmc')

    then:
    assertThat(slackChannel).isEqualTo("#cmc-builds")
  }

  def "getContactSlackChannel() with non existing product should throw exception"() {

    when:
    def slackChannel = teamConfig.getContactSlackChannel('idontexist')

    then:
    thrown RuntimeException
  }

  def "getContactSlackChannel() with product having empty slackchannel mapping should throw exception"() {

    when:
    def slackChannel = teamConfig.getContactSlackChannel('dm')

    then:
    thrown RuntimeException
  }

  def "getBuildNoticesSlackChannel() with non empty default value should return value back"() {
    def productName = 'idontexist'

    when:
    steps.env >> [
      BUILD_NOTICE_SLACK_CHANNEL: "#test-channel"
    ]
    def slackChannel = slackContactTeamConfig.getBuildNoticesSlackChannel(productName)

    then:
    assertThat(slackChannel).isEqualTo("#test-channel")
  }

  def "getBuildNoticesSlackChannel() with empty value should return mapping from team config"() {

    when:
    steps.env >> []
    def slackChannel = teamConfig.getBuildNoticesSlackChannel('cmc')

    then:
    assertThat(slackChannel).isEqualTo("#cmc-builds")
  }

  def "getBuildNoticesSlackChannel() with non existing product should throw exception"() {

    when:
    steps.env >> []
    def slackChannel = teamConfig.getBuildNoticesSlackChannel('idontexist')

    then:
    thrown RuntimeException
  }

  def "getBuildNoticesSlackChannel() with product having empty slackchannel mapping should throw exception"() {

    when:
    steps.env >> []
    def slackChannel = teamConfig.getBuildNoticesSlackChannel('dm')

    then:
    thrown RuntimeException
  }
}
