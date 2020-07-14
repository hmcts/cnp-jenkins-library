package uk.gov.hmcts.pipeline

import spock.lang.Specification
import uk.gov.hmcts.contino.JenkinsStepMock

class CVEPublisherTest extends Specification {

  def cvePublisher
  def steps

  def setup() {
    steps = Mock(JenkinsStepMock.class)
    steps.getEnv() >> [
      GIT_URL: 'http://example.com'
    ]
    def closure
    steps.withCredentials(_, { closure = it }) >> { closure.call() }

    cvePublisher = new CVEPublisher(
      'https://pipeline-metrics.documents.azure.com/',
      steps
    )
  }

  def "Publishing CVE report does not throw unhandled error"() {
    when:
    cvePublisher.publishCVEReport([
      dependencies: [
        something: '1.0'
      ]
    ])

    then:
    notThrown()
  }
}
