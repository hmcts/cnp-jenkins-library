package uk.gov.hmcts.pipeline

import spock.lang.Specification
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.JenkinsStepMock

import static org.assertj.core.api.Assertions.assertThat

class TeamConfigTest extends Specification {

  def steps
  def teamConfig
  def slackTeamConfig
  static def response = ["content": ["cmc":["team":"Money Claims","namespace":"money-claims","defaultSlackChannel":"#cmc-builds"],
                                     "bar":["team":"Fees/Pay","namespace":"fees-pay","defaultSlackChannel":"#fees-builds"],
                                     "ccd":["namespace":"ccd","defaultSlackChannel":"#ccd-builds"],
                                     "dm":["team":"CCD","defaultSlackChannel":""],
                                     "product":["team":"hmcts","defaultSlackChannel":"#product-builds"],
                                     "bulk-scan":["team":"Software Engineering","namespace":"rpe","defaultSlackChannel":"#rpe-builds"]]]

  void setup() {
    steps = Mock(JenkinsStepMock.class)
    steps.readYaml([text: response.content]) >> response.content
    steps.httpRequest(_) >> response
    teamConfig = new TeamConfig(steps)
    slackTeamConfig = new TeamConfig(steps, new AppPipelineConfig())
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

  def "getSlackChannel() with non empty default value should return value back"() {
    def productName = 'idontexist'
    def config = new AppPipelineConfig()
    config.slackChannel = "#test-channel"
    def teamConfigWithChannel = new TeamConfig(steps, config)
    when:
    def slackChannel = teamConfigWithChannel.getSlackChannel(productName)

    then:
    assertThat(slackChannel).isEqualTo(config.slackChannel)
  }

  def "getSlackChannel() with empty value should return mapping from team config"() {

    when:
    def slackChannel = slackTeamConfig.getSlackChannel('cmc')

    then:
    assertThat(slackChannel).isEqualTo("#cmc-builds")
  }

  def "getSlackChannel() with non existing product should throw exception"() {

    when:
    def slackChannel = slackTeamConfig.getSlackChannel('idontexist')

    then:
    thrown RuntimeException
  }

  def "getSlackChannel() with product having empty slackchannel mapping should throw exception"() {

    when:
    def slackChannel = slackTeamConfig.getSlackChannel('dm')

    then:
    thrown RuntimeException
  }

}
