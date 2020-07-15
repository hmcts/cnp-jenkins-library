package uk.gov.hmcts.contino

import spock.lang.Specification

class YarnBuilderTest extends Specification {
  static final String YARN_CMD = 'yarn'
  static final String PACT_BROKER_URL = "https://pact-broker.platform.hmcts.net"
  private static final LinkedHashMap<String, Object> NODE_JS_CVE_REPORT = [
    summary        : [
      vulnerabilities     : [info: 0, low: 1, moderate: 0, high: 0, critical: 0],
      dependencies        : 555,
      devDependencies     : 0,
      optionalDependencies: 0,
      totalDependencies   : 555
    ],
    vulnerabilities: [
      [
        type: 'auditAdvisory',
        data: [
          resolution: [
            id      : 1523,
            path    : 'pug>pug-filters>constantinople>babel-types>lodash',
            dev     : false,
            optional: false, bundled: false
          ],
          advisory  : [
            findings           : [
              [
                version: '4.17.15',
                paths  : ['request-promise>request-promise-core>lodash']
              ]
            ],
            id                 : 1523,
            created            : '2020-05-20T01:36:49.357Z',
            updated            : '2020-07-10T19:23:46.395Z',
            deleted            : null,
            title              : 'Prototype Pollution',
            found_by           : [link: '', name: 'posix', email: ''],
            reported_by        : [link: '', name: 'posix', email: ''],
            module_name        : 'lodash',
            cves               : ['CVE-2019-10744'],
            vulnerable_versions: '<4.17.19',
            patched_versions   : '>=4.17.19',
            overview           : "Versions of `lodash` prior to 4.17.19 are vulnerable to Prototype Pollution.  The function `zipObjectDeep` allows a malicious user to modify the prototype of `Object` if the property identifiers are user-supplied. Being affected by this issue requires zipping objects based on user-provided property arrays.  \n\nThis vulnerability causes the addition or modification of an existing property that will exist on all objects and may lead to Denial of Service or Code Execution under specific circumstances.",
            recommendation     : "Upgrade to version 4.17.19 or later.",
            references         : "- [HackerOne Report](https://hackerone.com/reports/712065)\n- [GitHub Issue](https://github.com/lodash/lodash/issues/4744)",
            access             : 'public', severity: 'low', cwe: 'CWE-471', metadata: [module_type: '', exploitability: 3, affected_components: ''], url: 'https://npmjs.com/advisories/1523']],
      ],
    ]
  ]

  def steps

  YarnBuilder builder

  def setup() {
    steps = Mock(JenkinsStepMock.class)

    def sampleCVEReport = new File(this.getClass().getClassLoader().getResource('yarn-audit-report-no-issues.txt').toURI()).text
    steps.readFile(_ as String) >> sampleCVEReport
    def closure
    steps.withCredentials(_, { closure = it }) >> { closure.call() }

    builder = new YarnBuilder(steps)
  }

  def "build calls 'yarn install' and 'yarn lint'"() {
    when:
      builder.build()
    then:
      1 * steps.sh(['script':'yarn check &> /dev/null', 'returnStatus':true])
      1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('--mutex network install --frozen-lockfile') })
      1 * steps.sh({ it.contains('touch .yarn_dependencies_installed') })
      1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('lint') })
  }

  def "test calls 'yarn test' and 'yarn test:coverage' and 'yarn test:a11y'"() {
    when:
    builder.test()
    then:
    1 * steps.sh(['script': 'yarn check &> /dev/null', 'returnStatus': true])
    1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('--mutex network install --frozen-lockfile') })
    1 * steps.sh({ it.contains('touch .yarn_dependencies_installed') })
    1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('test') })
    1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('test:coverage') })
    1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('test:a11y') })
  }

  def "sonarScan calls 'yarn sonar-scan'"() {
    when:
      builder.sonarScan()
    then:
      1 * steps.sh(['script':'yarn check &> /dev/null', 'returnStatus':true])
      1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('--mutex network install --frozen-lockfile') })
      1 * steps.sh({ it.contains('touch .yarn_dependencies_installed') })
      1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('sonar-scan') })
  }

  def "smokeTest calls 'yarn test:smoke'"() {
    when:
      builder.smokeTest()
    then:
      1 * steps.sh(['script':'yarn check &> /dev/null', 'returnStatus':true])
      1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('--mutex network install --frozen-lockfile') })
      1 * steps.sh({ it.contains('touch .yarn_dependencies_installed') })
      1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('test:smoke') })
  }

  def "functionalTest calls 'yarn test:functional'"() {
    when:
      builder.functionalTest()
    then:
      1 * steps.sh(['script':'yarn check &> /dev/null', 'returnStatus':true])
      1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('--mutex network install --frozen-lockfile') })
      1 * steps.sh({ it.contains('touch .yarn_dependencies_installed') })
      1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('test:functional') })
  }

  def "apiGatewayTest calls 'yarn test:apiGateway'"() {
    when:
      builder.apiGatewayTest()
    then:
      1 * steps.sh(['script':'yarn check &> /dev/null', 'returnStatus':true])
      1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('--mutex network install --frozen-lockfile') })
      1 * steps.sh({ it.contains('touch .yarn_dependencies_installed') })
      1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('test:apiGateway') })
  }

  def "crossBrowserTest calls 'yarn test:crossbrowser'"() {
    when:
    builder.crossBrowserTest()
    then:
    1 * steps.withSauceConnect({ it.startsWith('reform_tunnel') }, _ as Closure)
    when:
    builder.yarn("test:crossbrowser")
    then:
    1 * steps.sh(['script': 'yarn check &> /dev/null', 'returnStatus': true])
    1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('--mutex network install --frozen-lockfile') })
    1 * steps.sh({ it.contains('touch .yarn_dependencies_installed') })
    1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('test:crossbrowser') })
  }

  def "mutationTest calls 'yarn test:mutation'"() {
    when:
        builder.mutationTest()
    then:
        1 * steps.sh(['script':'yarn check &> /dev/null', 'returnStatus':true])
        1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('--mutex network install --frozen-lockfile') })
        1 * steps.sh({ it.contains('touch .yarn_dependencies_installed') })
        1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('test:mutation') })
  }

  def "securityCheck calls 'yarn audit'"() {
    when:
      builder.securityCheck()
    then:
      1 * steps.sh({ it.contains('audit') })
  }

  def "full functional tests calls 'yarn test:fullfunctional'"() {
    when:
        builder.fullFunctionalTest()
    then:
        1 * steps.sh(['script':'yarn check &> /dev/null', 'returnStatus':true])
        1 * steps.sh({ it.startsWith(YARN_CMD) && it.contains('--mutex network install --frozen-lockfile') })
        1 * steps.sh({ it.contains('touch .yarn_dependencies_installed') })
        1*steps.sh({ it.startsWith(YARN_CMD) && it.contains('test:fullfunctional') })
  }

  def "runProviderVerification triggers a yarn hook with publish"() {
    setup:
      def version = "v3r510n"
      def publishResults = true
    when:
      builder.runProviderVerification(PACT_BROKER_URL, version, publishResults)
    then:
      1 * steps.sh({ it.contains("PACT_BROKER_URL=${PACT_BROKER_URL} PACT_PROVIDER_VERSION=${version} yarn test:pact:verify-and-publish") })
  }

  def "runProviderVerification triggers a yarn hook without publish"() {
    setup:
      def version = "v3r510n"
      def publishResults = false
    when:
      builder.runProviderVerification(PACT_BROKER_URL, version, publishResults)
    then:
      1 * steps.sh({ it.contains("PACT_BROKER_URL=${PACT_BROKER_URL} PACT_PROVIDER_VERSION=${version} yarn test:pact:verify") })
  }

  def "runConsumerTests triggers a yarn hook"() {
    setup:
      def version = "v3r510n"
    when:
      builder.runConsumerTests(PACT_BROKER_URL, version)
    then:
      1 * steps.sh({ it.contains("PACT_BROKER_URL=${PACT_BROKER_URL} PACT_CONSUMER_VERSION=${version} yarn test:pact:run-and-publish") })
  }

  def "prepareCVEReport converts json lines report to groovy object"() {
    given:
      def sampleCVEReport = new File(this.getClass().getClassLoader().getResource('yarn-audit-report.txt').toURI()).text
    when:
      def result = builder.prepareCVEReport(sampleCVEReport, null)
    then:
    result == NODE_JS_CVE_REPORT
  }

  def "prepareCVEReport converts json lines report to groovy object with suppressions"() {
    given:
    def sampleCVEReport = new File(this.getClass().getClassLoader().getResource('yarn-audit-report.txt').toURI()).text
    def sampleCVESuppressionsReport = new File(this.getClass().getClassLoader().getResource('yarn-audit-report-suppressed.txt').toURI()).text
    def suppressed = NODE_JS_CVE_REPORT.vulnerabilities

    when:
    def result = builder.prepareCVEReport(sampleCVEReport, sampleCVESuppressionsReport)
    then:
    result == NODE_JS_CVE_REPORT << [suppressed: suppressed]
  }
}
