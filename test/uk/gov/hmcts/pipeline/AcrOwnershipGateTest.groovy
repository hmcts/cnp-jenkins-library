package uk.gov.hmcts.pipeline

import spock.lang.Specification

import static org.assertj.core.api.Assertions.assertThat

class AcrOwnershipGateTest extends Specification {

  AcrOwnershipGate gate = new AcrOwnershipGate()

  def "off mode should bypass checks"() {
    when:
    def decision = gate.evaluate('off', 'recipes', 'frontend', 'another/repo')

    then:
    assertThat(decision.decision).isEqualTo('allow')
    assertThat(decision.reasonCode).isEqualTo('MODE_OFF_BYPASS')
  }

  def "enforce mode should allow matching repository"() {
    when:
    def decision = gate.evaluate('enforce', 'recipes', 'frontend', 'recipes/frontend')

    then:
    assertThat(decision.decision).isEqualTo('allow')
    assertThat(decision.reasonCode).isEqualTo('REGEX_MATCH')
    assertThat(decision.regexMatched).isEqualTo(true)
  }

  def "enforce mode should allow matching repository tag"() {
    when:
    def decision = gate.evaluate('enforce', 'recipes', 'frontend', 'recipes/frontend:pr-123-anything-goes')

    then:
    assertThat(decision.decision).isEqualTo('allow')
    assertThat(decision.reasonCode).isEqualTo('REGEX_MATCH')
  }

  def "enforce mode should allow when product appears in repository prefix"() {
    when:
    def decision = gate.evaluate('enforce', 'recipes', 'frontend', 'hmcts-recipes/frontend')

    then:
    assertThat(decision.decision).isEqualTo('allow')
    assertThat(decision.reasonCode).isEqualTo('REGEX_MATCH')
  }

  def "audit mode should deny non matching repository"() {
    when:
    def decision = gate.evaluate('audit', 'recipes', 'frontend', 'other-team/frontend')

    then:
    assertThat(decision.decision).isEqualTo('deny')
    assertThat(decision.reasonCode).isEqualTo('REGEX_MISS')
    assertThat(gate.shouldBlock(decision)).isFalse()
  }

  def "enforce mode should block non matching repository"() {
    when:
    def decision = gate.evaluate('enforce', 'recipes', 'frontend', 'other-team/frontend')

    then:
    assertThat(decision.decision).isEqualTo('deny')
    assertThat(gate.shouldBlock(decision)).isTrue()
  }

  def "allow list should allow repository outside regex"() {
    when:
    def decision = gate.evaluate('enforce', 'recipes', 'frontend', 'shared/common-image:pr-99-custom', ['shared/common-image'])

    then:
    assertThat(decision.decision).isEqualTo('allow')
    assertThat(decision.reasonCode).isEqualTo('ALLOWLIST_MATCH')
    assertThat(decision.allowListMatched).isEqualTo(true)
  }

  def "normalize PR product naming before regex"() {
    when:
    def decision = gate.evaluate('enforce', 'pr-123-recipes', 'frontend', 'recipes/frontend')

    then:
    assertThat(decision.decision).isEqualTo('allow')
    assertThat(decision.product).isEqualTo('recipes')
  }

  def "invalid input should deny outside off mode"() {
    when:
    def decision = gate.evaluate('audit', null, 'frontend', 'recipes/frontend')

    then:
    assertThat(decision.decision).isEqualTo('deny')
    assertThat(decision.reasonCode).isEqualTo('INVALID_INPUT')
  }
}
