package uk.gov.hmcts.pipeline

import spock.lang.Specification
import uk.gov.hmcts.contino.CosmosDbTargetResolver
import uk.gov.hmcts.contino.JenkinsStepMock

class CVEPublisherTest extends Specification {

  def steps
  def resolver
  def envVars

  def setup() {
    envVars = [
      CVE_DASHBOARD_URL    : 'https://cve-dashboard.example',
      CVE_DASHBOARD_API_KEY: 'secret-api-key',
      TEAM_NAME            : 'CCD',
      GIT_URL              : 'https://github.com/hmcts/ccd-admin-web.git',
      BRANCH_NAME          : 'master',
      BUILD_TAG            : 'jenkins-ccd-admin-web-123',
      BUILD_URL            : 'https://jenkins.example/job/ccd-admin-web/123/'
    ]
    steps = Mock(JenkinsStepMock)
    steps.env >> envVars
    resolver = Mock(CosmosDbTargetResolver)
    resolver.databaseName() >> 'cve-db'
  }

  def "publishes dashboard snapshot after Cosmos publish fails open"() {
    given:
      def publisher = new CVEPublisher(steps, true, resolver)
      def report = [vulnerabilities: [[module_name: 'lodash', cves: ['CVE-2026-1001'], severity: 'high']]]

    when:
      publisher.publishCVEReport('node', report)

    then:
      1 * steps.echo("Publishing CVE report")
      1 * steps.azureCosmosDBCreateDocument(_ as LinkedHashMap) >> { throw new RuntimeException('cosmos down') }
      1 * steps.echo({ it.contains("Unable to publish CVE report") && it.contains("cosmos down") })
      1 * steps.httpRequest(_ as LinkedHashMap) >> [status: 200]
  }

  def "rethrows strict Cosmos publish errors after attempting dashboard publish"() {
    given:
      def publisher = new CVEPublisher(steps, false, resolver)
      def report = [vulnerabilities: [[module_name: 'lodash', cves: ['CVE-2026-1001'], severity: 'high']]]

    when:
      publisher.publishCVEReport('node', report)

    then:
      def error = thrown(RuntimeException)
      error.message == 'cosmos down'
      1 * steps.echo("Publishing CVE report")
      1 * steps.azureCosmosDBCreateDocument(_ as LinkedHashMap) >> { throw new RuntimeException('cosmos down') }
      1 * steps.httpRequest(_ as LinkedHashMap) >> [status: 200]
      0 * steps.echo({ it.contains("Unable to publish CVE report") })
  }
}
