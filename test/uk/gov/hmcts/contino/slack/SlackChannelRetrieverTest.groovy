package uk.gov.hmcts.contino.slack

import spock.lang.Specification
import uk.gov.hmcts.contino.JenkinsStepMock

import static org.assertj.core.api.Assertions.assertThat

class SlackChannelRetrieverTest extends Specification {

  def steps = Mock(JenkinsStepMock.class)
  def retriever = new SlackChannelRetriever(steps)

  def "returns provided channel when change author is empty"() {
    expect:
    retriever.retrieve("#team-channel", null) == "#team-channel"
    retriever.retrieve("#team-channel", "") == "#team-channel"
  }

  def "returns username with at-prefix for legacy mapping values"() {
    given:
    steps.httpRequest(_) >> [content: '{"users":[{"github":"alice","slack":"alice.smith"}]}']

    when:
    def channel = retriever.retrieve("#team-channel", "alice")

    then:
    assertThat(channel).isEqualTo("@alice.smith")
  }

  def "returns raw Slack user id when mapping contains id without at-prefix"() {
    given:
    steps.httpRequest(_) >> [content: '{"users":[{"github":"alice","slack":"U08HZPKQR1C"}]}']

    when:
    def channel = retriever.retrieve("#team-channel", "alice")

    then:
    assertThat(channel).isEqualTo("U08HZPKQR1C")
  }

  def "returns raw Slack user id when mapping contains id with at-prefix"() {
    given:
    steps.httpRequest(_) >> [content: '{"users":[{"github":"alice","slack":"@U08HZPKQR1C"}]}']

    when:
    def channel = retriever.retrieve("#team-channel", "alice")

    then:
    assertThat(channel).isEqualTo("U08HZPKQR1C")
  }

  def "returns null when no mapping is found"() {
    given:
    steps.httpRequest(_) >> [content: '{"users":[{"github":"someone-else","slack":"U08HZPKQR1C"}]}']

    expect:
    retriever.retrieve("#team-channel", "alice") == null
  }
}
