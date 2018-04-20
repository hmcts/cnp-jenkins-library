import com.lesfurets.jenkins.unit.BasePipelineTest
import groovy.json.JsonSlurperClassic
import groovy.mock.interceptor.MockFor
import groovy.mock.interceptor.StubFor
import org.junit.Test
import uk.gov.hmcts.contino.*

import static com.lesfurets.jenkins.unit.global.lib.LibraryConfiguration.library
import static uk.gov.hmcts.contino.ProjectSource.projectSource

class withJavaPipelineOnMasterTests extends BasePipelineTest {

  // get the 'project' directory
  String projectDir = (new File(this.getClass().getResource("exampleJavaPipeline.jenkins").toURI())).parentFile.parentFile.parentFile.parentFile

  withJavaPipelineOnMasterTests() {
    super.setUp()
    binding.setVariable("scm", null)
    binding.setVariable("Jenkins", [instance: new MockJenkins(new MockJenkinsPluginManager([new MockJenkinsPlugin('sonar', true) ] as MockJenkinsPlugin[]))])
    binding.setVariable("env", [
      BRANCH_NAME: "master", TEST_URL:'', SUBSCRIPTION_NAME:'', ARM_CLIENT_ID:'', ARM_CLIENT_SECRET:'', ARM_TENANT_ID:'',
      ARM_SUBSCRIPTION_ID:'', STORE_rg_name_template:'', STORE_sa_name_template:'', STORE_sa_container_name_template:''])

    def library = library()
      .name('Infrastructure')
      .targetPath(projectDir)
      .retriever(projectSource(projectDir))
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
    helper.registerAllowedMethod("sh", [Map.class], { return "" })
    helper.registerAllowedMethod('fileExists', [String.class], { c -> true })
    helper.registerAllowedMethod("timestamps", [Closure.class], null)
    helper.registerAllowedMethod("withSonarQubeEnv", [String.class, Closure.class], null)
    helper.registerAllowedMethod("waitForQualityGate", { [status: 'OK'] })
    helper.registerAllowedMethod("httpRequest", [LinkedHashMap.class], { return ['content': '']} )
    helper.registerAllowedMethod("writeFile", [LinkedHashMap.class], { })
    helper.registerAllowedMethod("lock", [String.class, Closure.class], null)
    helper.registerAllowedMethod("scmServiceRegistration", [String.class], {})
    helper.registerAllowedMethod("wrap", [LinkedHashMap, Closure.class], null)
  }

  @Test
  void PipelineExecutesExpectedStepsInExpectedOrder() {
    def mockBuilder = new MockFor(GradleBuilder)
    mockBuilder.demand.with {
      build(1) {}
      test(1) {}
      securityCheck(1) {}
      sonarScan(1) {}
      smokeTest(1) {} //aat-staging
      functionalTest(1) {}
      smokeTest(3) {} // aat-prod, prod-staging, prod-prod
    }

    def mockDeployer = new MockFor(JavaDeployer)
    mockDeployer.ignore.getServiceUrl() { env, slot -> return null} // we don't care when or how often this is called
    mockDeployer.demand.with {
      // aat-staging
      deploy() {}
      healthCheck() { env, slot -> return null }
      // aat-prod
      healthCheck() { env, slot -> return null }
      // prod-staging
      deploy() {}
      healthCheck() { env, slot -> return null }
      // prod-prod
      healthCheck() { env, slot -> return null }
    }

    def stubJsonSlurperClassic = new StubFor(JsonSlurperClassic)
    stubJsonSlurperClassic.ignore.parseText() { return ['azure_client_id': 'azure_client_id']}

    stubJsonSlurperClassic.use {
      mockBuilder.use {
        mockDeployer.use {
          runScript("testResources/exampleJavaPipeline.jenkins")
          printCallStack()
        }
      }
    }
  }
}

