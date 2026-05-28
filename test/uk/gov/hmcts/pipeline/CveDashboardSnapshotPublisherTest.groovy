package uk.gov.hmcts.pipeline

import groovy.json.JsonSlurperClassic
import spock.lang.Specification
import uk.gov.hmcts.contino.JenkinsStepMock

class CveDashboardSnapshotPublisherTest extends Specification {

  def steps
  def envVars
  CveDashboardSnapshotPublisher publisher

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
    publisher = new CveDashboardSnapshotPublisher(steps)
  }

  def "publishes normalized node snapshot payload"() {
    given:
      def request
      def report = [
        vulnerabilities: [
          [
            module_name: 'lodash',
            cves       : ['CVE-2026-1001', 'GHSA-xxxx-yyyy-zzzz'],
            severity   : 'moderate',
            cvss       : [score: 5.5]
          ],
          [
            module_name: 'lodash',
            cves       : ['CVE-2026-1001'],
            severity   : 'high',
            cvss       : [score: 7.8]
          ]
        ],
        suppressed     : [
          [
            module_name: 'legacy-helper',
            cves       : ['cve-2026-1001'],
            severity   : 'low',
            cvss       : [score: 3.1]
          ],
          [
            module_name: 'old-only',
            cves       : ['CVE-2026-1002'],
            severity   : 'critical',
            cvss       : [score: 9.8]
          ]
        ]
      ]

    when:
      publisher.publishSnapshot('node', report)

    then:
      1 * steps.httpRequest(_ as LinkedHashMap) >> { LinkedHashMap args ->
        request = args
        [status: 200]
      }

      request.url == 'https://cve-dashboard.example/api/cves/snapshots'
      request.httpMode == 'POST'
      request.customHeaders == [
        [name: 'X-API-Key', value: 'secret-api-key', maskValue: true],
        [name: 'X-Request-Id', value: 'jenkins-ccd-admin-web-123']
      ]

      def payload = new JsonSlurperClassic().parseText(request.requestBody)
      payload.team == 'CCD'
      payload.repository == 'ccd-admin-web'
      payload.codebaseType == 'node'
      payload.branchName == 'master'
      payload.gitUrl == 'https://github.com/hmcts/ccd-admin-web.git'
      payload.sourceSystem == 'jenkins'
      payload.sourceRunId == 'jenkins-ccd-admin-web-123'
      payload.sourceUrl == 'https://jenkins.example/job/ccd-admin-web/123/'
      payload.items == [
        [
          cve               : 'CVE-2026-1001',
          severity          : 'high',
          activePackages    : ['lodash'],
          suppressedPackages: ['legacy-helper'],
          score             : '7.8'
        ],
        [
          cve               : 'CVE-2026-1002',
          severity          : 'critical',
          activePackages    : [],
          suppressedPackages: ['old-only'],
          score             : '9.8'
        ]
      ]
  }

  def "publishes normalized gradle snapshot payload"() {
    given:
      def request
      def report = [
        dependencies: [
          [
            fileName       : 'library-a.jar',
            packages       : [[id: 'pkg:maven/org.example/library-a@1.0.0']],
            vulnerabilities: [
              [name: 'CVE-2026-2001', severity: 'MEDIUM', cvssv3: [baseScore: 6.1]],
              [name: 'CWE-79: cross-site scripting', severity: 'MEDIUM', cvssv3: [baseScore: 6.1]]
            ]
          ],
          [
            fileName                  : 'library-b.jar',
            suppressedVulnerabilities: [
              [name: 'CVE-2026-2001', severity: 'LOW', cvssv2: [score: 4.3]],
              [name: 'CVE-2026-2002', severity: 'CRITICAL', cvssv3: [baseScore: 9.8]]
            ]
          ]
        ]
      ]

    when:
      publisher.publishSnapshot('java', report)

    then:
      1 * steps.httpRequest(_ as LinkedHashMap) >> { LinkedHashMap args ->
        request = args
        [status: 200]
      }

      def payload = new JsonSlurperClassic().parseText(request.requestBody)
      payload.items == [
        [
          cve               : 'CVE-2026-2001',
          severity          : 'medium',
          activePackages    : ['pkg:maven/org.example/library-a@1.0.0'],
          suppressedPackages: ['library-b.jar'],
          score             : '6.1'
        ],
        [
          cve               : 'CVE-2026-2002',
          severity          : 'critical',
          activePackages    : [],
          suppressedPackages: ['library-b.jar'],
          score             : '9.8'
        ]
      ]
  }

  def "removes packages from suppressed list when the same package is active"() {
    given:
      def request
      def report = [
        vulnerabilities: [
          [module_name: 'lodash', cves: ['CVE-2026-1001'], severity: 'high', cvss: [score: 7.8]]
        ],
        suppressed     : [
          [module_name: 'lodash', cves: ['CVE-2026-1001'], severity: 'high', cvss: [score: 7.8]],
          [module_name: 'legacy-helper', cves: ['CVE-2026-1001'], severity: 'low', cvss: [score: 3.1]]
        ]
      ]

    when:
      publisher.publishSnapshot('node', report)

    then:
      1 * steps.httpRequest(_ as LinkedHashMap) >> { LinkedHashMap args ->
        request = args
        [status: 200]
      }

      def payload = new JsonSlurperClassic().parseText(request.requestBody)
      payload.items == [
        [
          cve               : 'CVE-2026-1001',
          severity          : 'high',
          activePackages    : ['lodash'],
          suppressedPackages: ['legacy-helper'],
          score             : '7.8'
        ]
      ]
  }

  def "skips publishing when dashboard secrets are absent"() {
    given:
      envVars.remove('CVE_DASHBOARD_URL')

    when:
      publisher.publishSnapshot('node', [vulnerabilities: []])

    then:
      0 * steps.httpRequest(_)
      0 * steps.echo(_)
  }

  def "skips publishing when current branch is not allowed"() {
    given:
      envVars.BRANCH_NAME = 'feature/test'
      envVars.CVE_DASHBOARD_PUBLISH_BRANCHES = 'master,demo'

    when:
      publisher.publishSnapshot('node', [vulnerabilities: []])

    then:
      0 * steps.httpRequest(_)
      0 * steps.echo(_)
  }

  def "publishes when branch override allows current branch"() {
    given:
      def request
      envVars.BRANCH_NAME = 'demo'
      envVars.CVE_DASHBOARD_PUBLISH_BRANCHES = ' master, demo, , master '

    when:
      publisher.publishSnapshot('node', [
        vulnerabilities: [[module_name: 'lodash', cves: ['CVE-2026-1001'], severity: 'high']]
      ])

    then:
      1 * steps.httpRequest(_ as LinkedHashMap) >> { LinkedHashMap args ->
        request = args
        [status: 200]
      }

      def payload = new JsonSlurperClassic().parseText(request.requestBody)
      payload.branchName == 'demo'
  }

  def "normalizes Jenkins Git URLs in snapshot payload"() {
    given:
      def request
      envVars.GIT_URL = gitUrl

    when:
      publisher.publishSnapshot('node', [
        vulnerabilities: [[module_name: 'lodash', cves: ['CVE-2026-1001'], severity: 'high']]
      ])

    then:
      1 * steps.httpRequest(_ as LinkedHashMap) >> { LinkedHashMap args ->
        request = args
        [status: 200]
      }

      def payload = new JsonSlurperClassic().parseText(request.requestBody)
      payload.gitUrl == expectedGitUrl
      payload.repository == 'ccd-admin-web'

    where:
      gitUrl                                           | expectedGitUrl
      'git@github.com:hmcts/ccd-admin-web.git'        | 'https://github.com/hmcts/ccd-admin-web.git'
      'ssh://git@github.com/hmcts/ccd-admin-web.git'  | 'https://github.com/hmcts/ccd-admin-web.git'
      'http://github.com/hmcts/ccd-admin-web'         | 'https://github.com/hmcts/ccd-admin-web'
  }

  def "logs and continues when dashboard request fails"() {
    when:
      publisher.publishSnapshot('node', [
        vulnerabilities: [[module_name: 'lodash', cves: ['CVE-2026-1001'], severity: 'high']]
      ])

    then:
      1 * steps.httpRequest(_ as LinkedHashMap) >> { throw new RuntimeException('timeout') }
      1 * steps.echo({ it.contains("Unable to publish CVE dashboard snapshot") && it.contains("timeout") })
  }
}
