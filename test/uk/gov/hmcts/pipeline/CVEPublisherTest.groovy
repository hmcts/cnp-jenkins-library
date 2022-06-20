package uk.gov.hmcts.pipeline

import com.microsoft.azure.documentdb.DocumentClient
import spock.lang.Specification
import uk.gov.hmcts.contino.JenkinsStepMock

class CVEPublisherTest extends Specification {

  CVEPublisher cvePublisher
  def steps

  def documentClient

  def setup() {
    documentClient = Mock(DocumentClient.class)
    steps = Mock(JenkinsStepMock.class)
    steps.getEnv() >> [
      BRANCH_NAME: 'master',
      GIT_URL: 'http://example.com',
      COSMOSDB_TOKEN_KEY: 'abcd'
    ]
    cvePublisher = new CVEPublisher(
      steps,
      false,
      documentClient
    )
  }

  def "Publishing CVE report does not throw unhandled error"() {
    given:
    def report = [
      dependencies: [
        something: '1.0'
      ]
    ]

    when:
    cvePublisher.publishCVEReport('java', report)

    then:
    notThrown(Exception.class)

    1 * documentClient.createDocument('dbs/jenkins/colls/cve-reports', _, null, false)
    1 * documentClient.close()
  }
}
