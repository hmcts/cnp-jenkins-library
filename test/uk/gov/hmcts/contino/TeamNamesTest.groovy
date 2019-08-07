package uk.gov.hmcts.contino

import spock.lang.Specification

import static org.assertj.core.api.Assertions.assertThat

class TeamNamesTest extends Specification {

  def steps
  def teamNames
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
    teamNames = new TeamNames(steps)
  }

  def "getName() with product name starting pr- should return correct teamname"() {
    def productName = 'pr-12-bar'
    def expected = 'Fees/Pay'
    when:
    def teamName = teamNames.getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }

  def "getName() with non existing product name starting pr- should return default teamname"() {
    def productName = 'pr-12-nonexisting'
    def expected = TeamNames.DEFAULT_TEAM_NAME
    when:
    def teamName = teamNames.getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }

  def "getName() with valid product name should return correct team name"() {
    def productName = 'bulk-scan'
    def expected = 'Software Engineering'

    when:
    def teamName = teamNames.getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }

  def "getName() with valid product name without name should return default team name"() {
    def productName = 'ccd'
    def expected = TeamNames.DEFAULT_TEAM_NAME

    when:
    def teamName = teamNames.getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }

  def "getName() with non existing product name should return default teamname"() {
    def productName = 'idontexist'
    def expected = TeamNames.DEFAULT_TEAM_NAME

    when:
    def teamName = teamNames.getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }

  def "getNameSpace() with product name starting pr- should return correct namespace"() {
    def productName = 'pr-12-bar'
    def expected = 'fees-pay'
    when:
    def teamName = teamNames.getNameSpace(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }

  def "getNameSpace() with non existing product name starting pr- should throw exception"() {
    def productName = 'pr-12-nonexisting'
    def expected = TeamNames.DEFAULT_TEAM_NAME
    when:
    def teamName = teamNames.getNameSpace(productName)

    then:
    thrown RuntimeException
  }

  def "getNameSpace() with valid product name should return correct team namespace"() {
    def productName = 'bulk-scan'
    def expected = 'rpe'

    when:
    def teamName = teamNames.getNameSpace(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }

  def "getNameSpace() with valid product name without namespace should throw error"() {
    def productName = 'dm'

    when:
    teamNames.getNameSpace(productName)

    then:
    thrown RuntimeException
  }

  def "getNameSpace() with non existing product name should throw error"() {
    def productName = 'idontexist'

    when:
    def teamName = teamNames.getNameSpace(productName)

    then:
    thrown RuntimeException
  }

  def "getSlackChannel() with non empty default value should return value back"() {
    def productName = 'idontexist'
    def channel = "#test-channel"
    when:
    def slackChannel = teamNames.getSlackChannel(productName, channel)

    then:
    assertThat(slackChannel).isEqualTo(channel)
  }

  def "getSlackChannel() with empty value should return mapping from team config"() {

    when:
    def slackChannel = teamNames.getSlackChannel('cmc', "")

    then:
    assertThat(slackChannel).isEqualTo("#cmc-builds")
  }

  def "getSlackChannel() with non existing product should throw exception"() {

    when:
    def slackChannel = teamNames.getSlackChannel('idontexist', "")

    then:
    thrown RuntimeException
  }

  def "getSlackChannel() with product having empty slackchannel mapping should throw exception"() {

    when:
    def slackChannel = teamNames.getSlackChannel('dm', "")

    then:
    thrown RuntimeException
  }

}
