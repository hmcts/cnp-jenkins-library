package uk.gov.hmcts.contino

import spock.lang.Specification
import static org.assertj.core.api.Assertions.assertThat

class TerraformTagMapTest extends Specification {

  def "ToString"() {

    Map tags = [environment: 'preview', changeUrl: 'https://github.com/my-repo/pull/53']
    def expected = '{environment="preview",changeUrl="https://github.com/my-repo/pull/53"}'

    when:
      def mapString = new TerraformTagMap(tags).toString()

    then:
      assertThat(mapString).isEqualTo(expected)
  }
}
