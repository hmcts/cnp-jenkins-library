package withNightlyPipeline

import com.lesfurets.jenkins.unit.BasePipelineTest
import groovy.json.JsonSlurper
import spock.lang.Specification
import spock.lang.Unroll
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.AppPipelineDsl
import uk.gov.hmcts.contino.PipelineCallbacksConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.pipeline.CveDashboardSnapshotPublisher

class SectionNightlyTestsCveDashboardTest extends Specification {

  @Unroll
  def "nightly snapshot publishing: enabled=#enabled branch=#branch environment=#environment"() {
    given:
      def pipelineTest = new BasePipelineTest() {}
      pipelineTest.setUp()
      def helper = pipelineTest.helper
      def env = [
        BRANCH_NAME: branch,
        GIT_URL: 'https://github.com/hmcts/example-service.git',
        TEAM_NAME: 'ccd',
        BUILD_TAG: 'jenkins-example-service-nightly-1'
      ]
      if (environment) {
        env.NONPROD_ENVIRONMENT_NAME = environment
      }
      pipelineTest.binding.setVariable('env', env)
      def config = new AppPipelineConfig()
      def callbacks = new PipelineCallbacksConfig()
      def dsl = new AppPipelineDsl(null, callbacks, config)
      if (enabled) {
        if (publishingBranches) {
          dsl.enableCveDashboardIngestion(publishingBranches)
        } else {
          dsl.enableCveDashboardIngestion()
        }
      }

      def vaultRequests = []
      def snapshotRequests = []
      def securityChecks = 0
      def buildEnvironment
      def securityEnvironment
      helper.registerAllowedMethod('withEnv', [List, Closure], { variables, body ->
        def previous = new LinkedHashMap(env)
        variables.each { variable ->
          def pair = variable.split('=', 2)
          env[pair[0]] = pair[1]
        }
        try {
          body.call()
        } finally {
          env.clear()
          env.putAll(previous)
        }
      })
      helper.registerAllowedMethod('withAzureKeyvault', [LinkedHashMap, Closure], { arguments, body ->
        vaultRequests << arguments
        env.CVE_DASHBOARD_API_KEY = 'test-api-key'
        try {
          body.call()
        } finally {
          env.remove('CVE_DASHBOARD_API_KEY')
        }
      })
      helper.registerAllowedMethod('httpRequest', [LinkedHashMap], { arguments ->
        snapshotRequests << arguments
        [status: 201, content: 'accepted']
      })
      helper.registerAllowedMethod('withTeamSecrets', [AppPipelineConfig, String, Closure], { ignored, target, body -> body.call() })
      helper.registerAllowedMethod('stageWithAgent', [String, String, Closure], { name, product, body -> body.call() })
      helper.registerAllowedMethod('checkoutScm', [LinkedHashMap], {})
      helper.registerAllowedMethod('timeoutWithMsg', [LinkedHashMap, Closure], { arguments, body -> body.call() })
      helper.registerAllowedMethod('warnError', [String, Closure], { message, body -> body.call() })
      helper.registerAllowedMethod('highLevelDataSetup', [LinkedHashMap], {})
      def dashboardStep = pipelineTest.loadScript('vars/withCveDashboardSecretsIfEnabled.groovy')
      helper.registerAllowedMethod('withCveDashboardSecretsIfEnabled', [AppPipelineConfig, String, String, Closure], {
        pipelineConfig, product, targetEnvironment, body ->
          dashboardStep.call(pipelineConfig, product, targetEnvironment, body)
      })
      def nightly = pipelineTest.loadScript('vars/sectionNightlyTests.groovy')
      def builder = [
        setupToolVersion: {},
        build: { buildEnvironment = new LinkedHashMap(env) },
        securityCheck: {
          securityChecks++
          securityEnvironment = new LinkedHashMap(env)
          new CveDashboardSnapshotPublisher(nightly).publishSnapshot('node', [vulnerabilities: [[
            module_name: 'example-package', cves: ['CVE-2026-1001'], severity: 'high'
          ]]])
        }
      ]

    when:
      nightly.call(new PipelineCallbacksRunner(callbacks), config, [builder: builder], 'ccd', 'example-service', 'nonprod')

    then:
      securityChecks == 1
      vaultRequests.size() == expectedRequests
      snapshotRequests.size() == expectedRequests
      !buildEnvironment.containsKey('CVE_DASHBOARD_API_KEY')
      !env.containsKey('CVE_DASHBOARD_API_KEY')
      !env.containsKey('CVE_DASHBOARD_URL')
      !env.containsKey('CVE_DASHBOARD_PUBLISH_BRANCHES')
      if (expectedRequests) {
        assert vaultRequests[0].keyVaultURLOverride == 'https://ccd-aat.vault.azure.net/'
        assert vaultRequests[0].azureKeyVaultSecrets[0].envVariable == 'CVE_DASHBOARD_API_KEY'
        assert securityEnvironment.CVE_DASHBOARD_PUBLISH_BRANCHES == (publishingBranches ?: ['master']).join(',')
        assert snapshotRequests[0].url == "https://cve-dashboard.${environment ?: 'aat'}.platform.hmcts.net/api/cves/snapshots"
        assert snapshotRequests[0].customHeaders.find { it.name == 'X-API-Key' }.value == 'test-api-key'
        def payload = new JsonSlurper().parseText(snapshotRequests[0].requestBody)
        assert payload.repository == 'example-service'
        assert payload.branchName == branch
        assert payload.items[0].cve == 'CVE-2026-1001'
      }

    where:
      enabled | branch         | environment | publishingBranches        | expectedRequests
      true    | 'master'       | null        | null                      | 1
      true    | 'master'       | 'saat'      | null                      | 1
      false   | 'master'       | null        | null                      | 0
      true    | 'nightly-dev'  | null        | null                      | 0
      true    | 'nightly-dev'  | null        | ['master', 'nightly-dev'] | 1
  }
}
