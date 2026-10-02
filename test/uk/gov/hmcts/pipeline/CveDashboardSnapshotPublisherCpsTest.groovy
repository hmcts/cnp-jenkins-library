package uk.gov.hmcts.pipeline

import com.cloudbees.groovy.cps.Continuable
import com.cloudbees.groovy.cps.CpsTransformer
import org.codehaus.groovy.control.CompilerConfiguration
import spock.lang.Specification

class CveDashboardSnapshotPublisherCpsTest extends Specification {

  def "normalizes and sorts Java findings under the Jenkins CPS transform"() {
    given:
      def config = new CompilerConfiguration()
      config.addCompilationCustomizers(new CpsTransformer())
      def loader = new GroovyClassLoader(getClass().classLoader, config)
      loader.parseClass(new File('src/uk/gov/hmcts/pipeline/CveDashboardSnapshotPublisher.groovy'))
      def report = [dependencies: [
        [packages: [[id: 'pkg:maven/org.example/library-b@2.0']],
         suppressedVulnerabilities: [[name: 'CVE-2026-2002', severity: 'HIGH']]],
        [packages: [[id: 'pkg:maven/org.example/library-a@1.0']],
         vulnerabilities: [[name: 'CVE-2026-2001', severity: 'MEDIUM', cvssv3: [baseScore: 6.1]]]],
        [evidenceCollected: [
          vendorEvidence: [[source: 'gradle', name: 'groupid', value: 'org.example']],
          productEvidence: [[source: 'gradle', name: 'artifactid', value: 'library-c']]
         ], suppressedVulnerabilities: [[name: 'CVE-2026-2002', severity: 'HIGH']]],
        [fileName: 'clean.jar']
      ]]
      def binding = new Binding([steps: [env: [GIT_URL: 'https://github.com/hmcts/ccd-data-store-api.git']], report: report])
      def script = new GroovyShell(loader, binding, config).parse('''
        new uk.gov.hmcts.pipeline.CveDashboardSnapshotPublisher(steps).buildPayload('java', report)
      ''')

    when:
      def payload = new Continuable(script).run(null)

    then:
      payload.items == [
        [cve: 'CVE-2026-2001', severity: 'medium', score: '6.1',
         activePackages: ['org.example:library-a'], suppressedPackages: []],
        [cve: 'CVE-2026-2002', severity: 'high',
         activePackages: [], suppressedPackages: ['org.example:library-b', 'org.example:library-c']]
      ]

    cleanup:
      loader.close()
  }
}
