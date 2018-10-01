package uk.gov.hmcts.contino

import spock.lang.Specification

import static org.assertj.core.api.Assertions.assertThat

class TeamNamesTest extends Specification {

  def "bar"() {
    def productName = 'bar'
    def expected = 'Fees/Pay'

    when:
    def teamName = new TeamNames().getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }

  def "bulk-scan"() {
    def productName = 'bulk-scan'
    def expected = 'Software Engineering'

    when:
    def teamName = new TeamNames().getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }

  def "ccd"() {
    def productName = 'ccd'
    def expected = 'CCD'

    when:
    def teamName = new TeamNames().getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }

  def "cmc"() {
    def productName = 'cmc'
    def expected = 'Money Claims'

    when:
    def teamName = new TeamNames().getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }

  def "custard"() {
    def productName = 'custard'
    def expected = 'CNP'

    when:
    def teamName = new TeamNames().getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }

  def "div"() {
    def productName = 'div'
    def expected = 'Divorce'

    when:
    def teamName = new TeamNames().getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }

  def "dm"() {
    def productName = 'dm'
    def expected = 'Evidence Mment'

    when:
    def teamName = new TeamNames().getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }

  def "em"() {
    def productName = 'em'
    def expected = 'Evidence Mment'

    when:
    def teamName = new TeamNames().getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }

  def "fees"() {
    def productName = 'fees'
    def expected = 'Fees/Pay'

    when:
    def teamName = new TeamNames().getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }

  def "finrem"() {
    def productName = 'finrem'
    def expected = 'Financial Remedy'

    when:
    def teamName = new TeamNames().getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }

  def "ia"() {
    def productName = 'ia'
    def expected = 'Immigration'

    when:
    def teamName = new TeamNames().getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }

  def "idam"() {
    def productName = 'idam'
    def expected = 'IdAM'

    when:
    def teamName = new TeamNames().getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }

  def "payment"() {
    def productName = 'payment'
    def expected = 'Fees/Pay'

    when:
    def teamName = new TeamNames().getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }

  def "rhubarb"() {
    def productName = 'rhubarb'
    def expected = 'CNP'

    when:
    def teamName = new TeamNames().getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }

  def "sscs"() {
    def productName = 'sscs'
    def expected = 'SSCS'

    when:
    def teamName = new TeamNames().getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }

  def "rpe"() {
    def productName = 'rpe'
    def expected = 'Software Engineering'

    when:
    def teamName = new TeamNames().getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }

  def "default"() {
    def productName = 'idontexist'
    def expected = TeamNames.DEFAULT_TEAM_NAME

    when:
    def teamName = new TeamNames().getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }

}
