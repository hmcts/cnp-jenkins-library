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

  def "cet"() {
    def productName = 'cet'
    def expected = 'Civil Enforcement'

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
    def expected = 'CCD'

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
  
   def "fees-register"() {
    def productName = 'fees-register'
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

   def "ccpay"() {
    def productName = 'ccpay'
    def expected = 'Fees/Pay'

    when:
    def teamName = new TeamNames().getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }
  
    def "probate"() {
    def productName = 'probate'
    def expected = 'Probate'

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
  
    def "rhubarb-shared-infrastructure"() {
    def productName = 'rhubarb-shared-infrastructure'
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
  
    def "sscs-tya"() {
    def productName = 'sscs-tya'
    def expected = 'SSCS'

    when:
    def teamName = new TeamNames().getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }
 
    def "sscs-tribunals"() {
    def productName = 'sscs-tribunals'
    def expected = 'SSCS'

    when:
    def teamName = new TeamNames().getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }
 
    def "sscs-cor"() {
    def productName = 'sscs-cor'
    def expected = 'SSCS'

    when:
    def teamName = new TeamNames().getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }
  
  def "pr-47-sscs-cor"() {
    def productName = 'pr-47-sscs-cor'
    def expected = 'SSCS'

    when:
    def teamName = new TeamNames().getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }
  
    def "pr-417-sscs-cor"() {
    def productName = 'pr-417-sscs-cor'
    def expected = 'SSCS'

    when:
    def teamName = new TeamNames().getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }
 
    def "pr-12-snl"() {
    def productName = 'pr-12-snl'
    def expected = 'SnL'

    when:
    def teamName = new TeamNames().getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }
  def "snl"() {
    def productName = 'snl'
    def expected = 'SnL'

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
  def "rpa"() {
    def productName = 'rpa'
    def expected = 'Professional Applications'

    when:
    def teamName = new TeamNames().getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }
  
    def "jui"() {
    def productName = 'jui'
    def expected = 'Professional Applications'

    when:
    def teamName = new TeamNames().getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }
  
    def "pui"() {
    def productName = 'pui'
    def expected = 'Professional Applications'

    when:
    def teamName = new TeamNames().getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }
  
   def "coh"() {
    def productName = 'coh'
    def expected = 'Professional Applications'

    when:
    def teamName = new TeamNames().getName(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }
  
   def "ref"() {
    def productName = 'ref'
    def expected = 'Professional Applications'

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

  def "getNameNormalizedOrThow() should return team name if found"() {
    def productName = 'bulk-scan'
    def expected = "software-engineering"

    when:
    def teamName = new TeamNames().getNameNormalizedOrThrow(productName)

    then:
    assertThat(teamName).isEqualTo(expected)
  }

  def "getNameNormalizedOrThow() should throw exception if team name not found"() {
    def productName = 'idontexist'

    when:
    new TeamNames().getNameNormalizedOrThrow(productName)

    then:
    thrown RuntimeException
  }

}
