package withPipeline

import com.lesfurets.jenkins.unit.BasePipelineTest
import spock.lang.Specification
import uk.gov.hmcts.contino.AppPipelineConfig

class SectionBuildAndTestCveDashboardTest extends Specification {

  def helper
  def binding
  def script
  def keyVaultArguments
  def withEnvArguments

  def setup() {
    def pipelineTest = new BasePipelineTest() {}
    pipelineTest.setUp()
    helper = pipelineTest.helper
    binding = pipelineTest.binding
    binding.setVariable('env', [BRANCH_NAME: 'master'])
    helper.registerAllowedMethod("withEnv", [List.class, Closure.class], { args, body ->
      withEnvArguments = args
      body.call()
    })
    helper.registerAllowedMethod("withAzureKeyvault", [LinkedHashMap, Closure.class], { args, body ->
      keyVaultArguments = args
      body.call()
    })
    script = pipelineTest.loadScript('vars/sectionBuildAndTest.groovy')
  }

  def "does not load CVE dashboard secrets when ingestion is disabled"() {
    given:
      def called = false
      def config = new AppPipelineConfig()

    when:
      script.withCveDashboardSecretsIfEnabled(config, 'ccd', 'aat') {
        called = true
      }

    then:
      called
      keyVaultArguments == null
      withEnvArguments == null
  }

  def "loads default CCD environment CVE dashboard secrets when ingestion is enabled"() {
    given:
      def called = false
      def config = new AppPipelineConfig(cveDashboardIngestion: true)

    when:
      script.withCveDashboardSecretsIfEnabled(config, 'ccd', 'aat') {
        called = true
      }

    then:
      called
      keyVaultArguments.keyVaultURLOverride == 'https://ccd-aat.vault.azure.net/'
      keyVaultArguments.azureKeyVaultSecrets == [
        [secretType: 'Secret', name: 'cve-dashboard-cve-intake-api-key', version: '', envVariable: 'CVE_DASHBOARD_API_KEY']
      ]
      withEnvArguments == [
        'CVE_DASHBOARD_URL=https://cve-dashboard.aat.platform.hmcts.net',
        'CVE_DASHBOARD_PUBLISH_BRANCHES=master'
      ]
  }

  def "loads explicit CVE dashboard vault when configured"() {
    given:
      def config = new AppPipelineConfig(cveDashboardIngestion: true, cveDashboardVaultName: 'shared-cve-dashboard')

    when:
      script.withCveDashboardSecretsIfEnabled(config, 'ccd', 'aat') {}

    then:
      keyVaultArguments.keyVaultURLOverride == 'https://shared-cve-dashboard.vault.azure.net/'
  }

  def "does not load CVE dashboard secrets when branch is not allowed"() {
    given:
      def called = false
      binding.env.BRANCH_NAME = 'feature/test'
      def config = new AppPipelineConfig(cveDashboardIngestion: true)

    when:
      script.withCveDashboardSecretsIfEnabled(config, 'ccd', 'aat') {
        called = true
      }

    then:
      called
      keyVaultArguments == null
      withEnvArguments == null
  }

  def "loads CVE dashboard secrets when branch override allows current branch"() {
    given:
      binding.env.BRANCH_NAME = 'demo'
      def config = new AppPipelineConfig(
        cveDashboardIngestion: true,
        cveDashboardIngestionBranches: ['master', 'demo']
      )

    when:
      script.withCveDashboardSecretsIfEnabled(config, 'ccd', 'aat') {}

    then:
      keyVaultArguments.keyVaultURLOverride == 'https://ccd-aat.vault.azure.net/'
      withEnvArguments == [
        'CVE_DASHBOARD_URL=https://cve-dashboard.aat.platform.hmcts.net',
        'CVE_DASHBOARD_PUBLISH_BRANCHES=master,demo'
      ]
  }
}
