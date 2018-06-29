package uk.gov.hmcts.contino

import spock.lang.Specification
import static org.assertj.core.api.Assertions.assertThat

class RepositoryUrlTest extends Specification {

  def "should provide github short url" () {
    when:
    def shortUrl = new RepositoryUrl().getShort("https://github.com/hmcts/spring-boot-template/pull/101")

    then:
    assertThat(shortUrl).isEqualTo("hmcts/spring-boot-template")
  }
}
