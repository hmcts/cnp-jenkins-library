package uk.gov.hmcts.contino

import spock.lang.Specification
import static org.assertj.core.api.Assertions.assertThat

class RepositoryUrlTest extends Specification {

  static final URL = 'https://github.com/hmcts/spring-boot-template/pull/101'

  def "should provide github 'full' url" () {
    when:
      def fullUrl = new RepositoryUrl().getFull(URL)

    then:
      assertThat(fullUrl).isEqualTo("hmcts/spring-boot-template")
  }

  def "should provide github repo name" () {
    when:
      def shortUrl = new RepositoryUrl().getShort(URL)

    then:
      assertThat(shortUrl).isEqualTo("spring-boot-template")
  }
}
