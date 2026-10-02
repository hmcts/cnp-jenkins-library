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
            packages                  : [[id: 'pkg:maven/org.example/library-b@2.0.0']],
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
          activePackages    : ['org.example:library-a'],
          suppressedPackages: ['org.example:library-b'],
          score             : '6.1'
        ],
        [
          cve               : 'CVE-2026-2002',
          severity          : 'critical',
          activePackages    : [],
          suppressedPackages: ['org.example:library-b'],
          score             : '9.8'
        ]
      ]
  }

  def "normalizes Maven package identifiers without versions"() {
    when:
      def payload = publisher.buildPayload('java', [dependencies: [[
        packages: [[id: 'pkg:npm/unrelated@1.0.0'], [id: packageId]],
        vulnerabilities: [[name: 'CVE-2026-2001', severity: 'HIGH']]
      ]]])

    then:
      payload.items[0].activePackages == ['org.example:library-a']

    where:
      packageId << [
        'pkg:maven/org.example/library-a@1.0.0',
        'pkg:maven/org.example/library-a@1.0.0?type=jar#subpath',
        'pkg:maven/org.example/library-a',
        'pkg:maven/org%2Eexample/library%2Da@1.0.0',
        'org.example:library-a'
      ]
  }

  def "deduplicates Java package versions and gives active findings precedence"() {
    when:
      def payload = publisher.buildPayload('java', [dependencies: [
        [packages: [[id: 'pkg:maven/org.example/library-a@1.0']],
         vulnerabilities: [[name: 'CVE-2026-2001', severity: 'MEDIUM', cvssv3: [baseScore: 6.1]]]],
        [packages: [[id: 'pkg:maven/org.example/library-a@2.0']],
         vulnerabilities: [[name: 'CVE-2026-2001', severity: 'HIGH', cvssv3: [baseScore: 7.8]]]],
        [packages: [[id: 'pkg:maven/org.example/library-a@3.0']],
         suppressedVulnerabilities: [[name: 'CVE-2026-2001', severity: 'LOW']]],
        [packages: [[id: 'pkg:maven/org.example/library-b@1.0']],
         suppressedVulnerabilities: [[name: 'CVE-2026-2001', severity: 'LOW']]]
      ]])

    then:
      payload.items == [[
        cve: 'CVE-2026-2001', severity: 'high', score: '7.8',
        activePackages: ['org.example:library-a'], suppressedPackages: ['org.example:library-b']
      ]]
  }

  def "resolves Java coordinates from matching Gradle or POM evidence"() {
    when:
      def payload = publisher.buildPayload('java', [dependencies: [[
        fileName: 'library-a.jar',
        evidenceCollected: [
          vendorEvidence: [[source: source, name: 'groupid', value: 'org.example']],
          productEvidence: [[source: source, name: 'artifactid', value: 'library-a']]
        ],
        suppressedVulnerabilities: [[name: 'CVE-2026-2001', severity: 'HIGH']]
      ]]])

    then:
      payload.items[0].suppressedPackages == ['org.example:library-a']

    where:
      source << ['gradle', 'pom']
  }

  def "prefers Gradle coordinates over conflicting POM evidence"() {
    when:
      def payload = publisher.buildPayload('java', [dependencies: [[
        evidenceCollected: [
          vendorEvidence: [
            [source: 'gradle', name: 'groupid', value: 'org.example'],
            [source: 'pom', name: 'groupid', value: 'org.parent']
          ],
          productEvidence: [
            [source: 'gradle', name: 'artifactid', value: 'library-a'],
            [source: 'pom', name: 'artifactid', value: 'parent']
          ]
        ],
        vulnerabilities: [[name: 'CVE-2026-2001', severity: 'HIGH']]
      ]]])

    then:
      payload.items[0].activePackages == ['org.example:library-a']
  }

  def "does not publish a partial Java snapshot when coordinates cannot be resolved"() {
    given:
      def findings = [[name: 'CVE-2026-2002', severity: 'HIGH']]
      def unresolved = [fileName: 'unresolved.jar', packages: [[id: packageId]], evidenceCollected: evidence]
      unresolved[findingType] = findings

    when:
      publisher.publishSnapshot('java', [dependencies: [
        [packages: [[id: 'pkg:maven/org.example/library-a@1.0']],
         vulnerabilities: [[name: 'CVE-2026-2001', severity: 'HIGH']]],
        unresolved
      ]])

    then:
      0 * steps.httpRequest(_)
      1 * steps.echo({
        it.contains('Cannot resolve Maven coordinates') && it.contains('unresolved.jar') &&
          it.contains('refusing to publish an incomplete snapshot')
      })

    where:
      findingType                 | packageId                                | evidence
      'vulnerabilities'           | ''                                       | [:]
      'suppressedVulnerabilities' | 'pkg:npm/unrelated@1.0'                   | [:]
      'vulnerabilities'           | 'pkg:maven/org%ZZexample/library-a@1.0'   | [:]
      'vulnerabilities'           | ''                                       | [vendorEvidence: [[source: 'gradle', name: 'groupid', value: 'org.example']], productEvidence: [[source: 'pom', name: 'artifactid', value: 'library-a']]]
      'suppressedVulnerabilities' | ''                                       | [vendorEvidence: [[source: 'pom', name: 'groupid', value: 'org.example'], [source: 'pom', name: 'groupid', value: 'org.other']], productEvidence: [[source: 'pom', name: 'artifactid', value: 'library-a']]]
  }

  def "publishes an empty Java snapshot when unresolved dependencies have no CVEs"() {
    given:
      def request

    when:
      publisher.publishSnapshot('java', [dependencies: [
        [fileName: 'clean.jar'],
        [fileName: 'non-cve.jar', vulnerabilities: [[name: 'GHSA-xxxx-yyyy-zzzz']]]
      ]])

    then:
      1 * steps.httpRequest(_ as LinkedHashMap) >> { LinkedHashMap args ->
        request = args
        [status: 200]
      }
      new JsonSlurperClassic().parseText(request.requestBody).items == []
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

  def "logs request and response payloads with the request ID"() {
    given:
      def request
      String requestLog
      def requestId = envVars.BUILD_TAG

    when:
      publisher.publishSnapshot('node', [
        vulnerabilities: [[module_name: 'lodash', cves: ['CVE-2026-1001'], severity: 'high']]
      ])

    then:
      1 * steps.echo({ it.startsWith("CVE dashboard snapshot request (${requestId}): ") }) >> { args ->
        requestLog = args[0].toString()
      }
      1 * steps.httpRequest(_ as LinkedHashMap) >> { LinkedHashMap args ->
        request = args
        [status: status, content: responseBody]
      }
      1 * steps.echo("CVE dashboard snapshot response (${requestId}), status ${status}: ${responseBody}")
      (status >= 400 ? 1 : 0) * steps.echo("Unable to publish CVE dashboard snapshot '${status}'")

      requestLog == "CVE dashboard snapshot request (${requestId}): ${request.requestBody}"
      !requestLog.contains(envVars.CVE_DASHBOARD_API_KEY)
      request.consoleLogResponseBody == false
      request.customHeaders.find { it.name == 'X-API-Key' }.maskValue

    where:
      status | responseBody
      200    | '{"summary":{"total":1}}'
      400    | '{"error":{"code":"validation_error","errors":[{"field":"suppressedPackages","message":"Enter Maven coordinates"}]}}'
      500    | 'Internal server error'
  }

  def "logs an empty response body explicitly"() {
    when:
      publisher.publishSnapshot('node', [vulnerabilities: []])

    then:
      1 * steps.httpRequest(_ as LinkedHashMap) >> [status: 204]
      1 * steps.echo("CVE dashboard snapshot response (${envVars.BUILD_TAG}), status 204: <empty>")
  }

  def "redacts the API key if it appears in logged payloads"() {
    given:
      envVars.TEAM_NAME = envVars.CVE_DASHBOARD_API_KEY
      def request

    when:
      publisher.publishSnapshot('node', [vulnerabilities: []])

    then:
      1 * steps.httpRequest(_ as LinkedHashMap) >> { LinkedHashMap args ->
        request = args
        [status: 400, content: "Rejected ${envVars.CVE_DASHBOARD_API_KEY}"]
      }
      1 * steps.echo({
        it.startsWith('CVE dashboard snapshot request (') &&
          it.contains('"team":"*****"') && !it.contains(envVars.CVE_DASHBOARD_API_KEY)
      })
      1 * steps.echo("CVE dashboard snapshot response (${envVars.BUILD_TAG}), status 400: Rejected *****")
      1 * steps.echo("Unable to publish CVE dashboard snapshot '400'")

      new JsonSlurperClassic().parseText(request.requestBody).team == envVars.TEAM_NAME
  }
}
