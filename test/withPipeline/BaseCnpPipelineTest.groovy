package withPipeline

import com.lesfurets.jenkins.unit.BasePipelineTest
import uk.gov.hmcts.contino.MockJenkins
import uk.gov.hmcts.contino.MockJenkinsPlugin
import uk.gov.hmcts.contino.MockJenkinsPluginManager

import static com.lesfurets.jenkins.unit.global.lib.LibraryConfiguration.library
import static uk.gov.hmcts.contino.ProjectSource.projectSource

abstract class BaseCnpPipelineTest extends BasePipelineTest {

  BaseCnpPipelineTest(String branchName, String jenkinsFile) {
    super.setUp()

    // get the 'project' directory
    def projectDir = (new File(this.getClass().getClassLoader().getResource(jenkinsFile).toURI())).parentFile.parentFile.parentFile.parentFile

    binding.setVariable("scm", null)
    binding.setVariable("Jenkins", [instance: new MockJenkins(new MockJenkinsPluginManager([new MockJenkinsPlugin('sonar', true)] as MockJenkinsPlugin[]))])
    binding.setVariable("env", [
      BRANCH_NAME : branchName, TEST_URL: '', SUBSCRIPTION_NAME: '', ARM_CLIENT_ID: '', ARM_CLIENT_SECRET: '', ARM_TENANT_ID: '',
      ARM_SUBSCRIPTION_ID: '', JENKINS_SUBSCRIPTION_ID: '', STORE_rg_name_template: '', STORE_sa_name_template: '', STORE_sa_container_name_template: '',
      CHANGE_URL:'', CHANGE_BRANCH:'', BEARER_TOKEN:'', CHANGE_TITLE:'', GIT_COMMIT: ''])

    def library = library()
      .name('Infrastructure')
      .targetPath(projectDir.toString())
      .retriever(projectSource(projectDir.toString()))
      .defaultVersion("master")
      .allowOverride(true)
      .implicit(false)
      .build()
    helper.registerSharedLibrary(library)
    helper.registerAllowedMethod("deleteDir", null)
    helper.registerAllowedMethod("stash", [Map.class], null)
    helper.registerAllowedMethod("unstash", [String.class], null)
    helper.registerAllowedMethod("withEnv", [List.class, Closure.class], null)
    helper.registerAllowedMethod("ansiColor", [String.class, Closure.class], null)
    helper.registerAllowedMethod("withCredentials", [LinkedHashMap, Closure.class], null)
    helper.registerAllowedMethod("azureServicePrincipal", [LinkedHashMap], null)
    helper.registerAllowedMethod("usernamePassword", [LinkedHashMap], null)
    helper.registerAllowedMethod('fileExists', [String.class], { c -> c == 'localPath/infrastructure' })
    helper.registerAllowedMethod("timestamps", [Closure.class], null)
    helper.registerAllowedMethod("withSonarQubeEnv", [String.class, Closure.class], null)
    helper.registerAllowedMethod("waitForQualityGate", { [status: 'OK'] })
    helper.registerAllowedMethod("writeFile", [LinkedHashMap.class], {})
    helper.registerAllowedMethod("lock", [String.class, Closure.class], null)
    helper.registerAllowedMethod("scmServiceRegistration", [String.class], {})
    helper.registerAllowedMethod("scmServiceRegistration", [String.class, String.class], {})
    helper.registerAllowedMethod("wrap", [LinkedHashMap, Closure.class], null)
    helper.registerAllowedMethod("sh", [Map.class], { m ->
      if (m.get('script') == 'pwd') {
        return 'localPath'
      } else {
        return '{"azure_subscription": "fake_subscription_name","azure_client_id": "fake_client_id",' +
          '"azure_client_secret": "fake_secret","azure_tenant_id": "fake_tenant_id"}'
      }
    })
    helper.registerAllowedMethod("httpRequest", [LinkedHashMap.class], {
      return ['content': '{"azure_subscription": "fake_subscription_name","azure_client_id": "fake_client_id",' +
        '"azure_client_secret": "fake_secret","azure_tenant_id": "fake_tenant_id"}']
    })
    helper.registerAllowedMethod("milestone", null)
    helper.registerAllowedMethod("lock", [LinkedHashMap.class, Closure.class], null)
  }
}

