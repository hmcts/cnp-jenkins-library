package withPipeline

import com.lesfurets.jenkins.unit.BasePipelineTest
import uk.gov.hmcts.contino.EnvironmentDnsConfigTest
import uk.gov.hmcts.contino.MockDocker
import uk.gov.hmcts.contino.MockJenkins
import uk.gov.hmcts.contino.MockJenkinsPlugin
import uk.gov.hmcts.contino.MockJenkinsPluginManager

import uk.gov.hmcts.pipeline.EnvironmentApprovalsTest
import uk.gov.hmcts.pipeline.TeamConfigTest
import uk.gov.hmcts.contino.GithubAPITest

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
      CHANGE_URL:'', CHANGE_BRANCH:'', BEARER_TOKEN:'', CHANGE_TITLE:'', GIT_COMMIT: 'abcdefgh', GIT_URL: 'https://github.com/hmcts/cnp-plum-recipes-service.git'])
    binding.setVariable("docker", new MockDocker())

    def library = library()
      .name('Infrastructure')
      .targetPath(projectDir.toString())
      .retriever(projectSource(projectDir.toString()))
      .defaultVersion("master")
      .allowOverride(true)
      .implicit(false)
      .build()
    helper.registerSharedLibrary(library)
    helper.registerAllowedMethod("deleteDir",  [Integer, Closure.class], {})
    helper.registerAllowedMethod("withEnv", [List.class, Closure.class], null)
    helper.registerAllowedMethod("ansiColor", [String.class, Closure.class], null)
    helper.registerAllowedMethod("withCredentials", [LinkedHashMap, Closure.class], null)
    helper.registerAllowedMethod("azureServicePrincipal", [LinkedHashMap], null)
    helper.registerAllowedMethod("usernamePassword", [LinkedHashMap], null)
    helper.registerAllowedMethod('fileExists', [String.class], { c -> c == 'localPath/infrastructure' || c == 'Dockerfile' || c.startsWith('charts') })
    helper.registerAllowedMethod("withSonarQubeEnv", [String.class, Closure.class], null)
    helper.registerAllowedMethod("waitForQualityGate", { [status: 'OK'] })
    helper.registerAllowedMethod("writeFile", [LinkedHashMap.class], {})
    helper.registerAllowedMethod("lock", [String.class, Closure.class], null)
    helper.registerAllowedMethod("warnError", [String.class, Closure.class], null)
    helper.registerAllowedMethod("registerDns", [LinkedHashMap.class], {})
    helper.registerAllowedMethod("helmPublish", [LinkedHashMap], null)
    helper.registerAllowedMethod("azureCosmosDBCreateDocument", [LinkedHashMap], null)
    helper.registerAllowedMethod("retry", [Integer, Closure.class], {})
    helper.registerAllowedMethod("withAzureKeyvault", [List.class, Closure.class], { secrets, body ->
      body.call()
    })
    helper.registerAllowedMethod("withAzureKeyvault", [LinkedHashMap, Closure.class], {secrets, body ->
      body.call()
    })
    helper.registerAllowedMethod("slackSend", [LinkedHashMap], null)
    helper.registerAllowedMethod("sh", [Map.class], { m ->
      if (m.get('script') == 'pwd') {
        return 'localPath'
      }  else if(m.get('script').startsWith("kubectl get service")){
        return '{"apiVersion":"v1","kind":"Service","spec":{"clusterIP":"10.0.238.83","externalTrafficPolicy":"Cluster",' +
          '"loadBalancerIP":"10.10.33.250","selector":{"app":"traefik","release":"traefik"},"type":"LoadBalancer"},"status":{"loadBalancer":{"ingress":[{"ip":"10.10.33.250"}]}}}'
      }
      else {
        return '{"azure_subscription": "fake_subscription_name","azure_client_id": "fake_client_id",' +
          '"azure_client_secret": "fake_secret","azure_tenant_id": "fake_tenant_id"}'
      }
    })

    helper.registerAllowedMethod("httpRequest", [LinkedHashMap.class], { m ->
      if (m.get('url') == 'https://raw.githubusercontent.com/hmcts/cnp-jenkins-config/master/team-config.yml') {
        return TeamConfigTest.response
      } else if (m.get('url') == 'https://raw.githubusercontent.com/hmcts/cnp-jenkins-config/master/environment-approvals.yml') {
        return EnvironmentApprovalsTest.response
      }
      else if (m.get('url') == 'https://raw.githubusercontent.com/hmcts/cnp-jenkins-config/master/private-dns-config.yml'){
        return EnvironmentDnsConfigTest.response
      }
      else if (m.get('url').startsWith("https://api.github.com/repos") && m.get('url').endsWith("/labels")){
        return GithubAPITest.response
      }
      else {
        return ['content': '{"azure_subscription": "fake_subscription_name","azure_client_id": "fake_client_id",' +
           '"azure_client_secret": "fake_secret","azure_tenant_id": "fake_tenant_id"}']
      }
    })
    helper.registerAllowedMethod("milestone",  [Integer, Closure.class], {})
    helper.registerAllowedMethod("lock", [LinkedHashMap.class, Closure.class], null)
    helper.registerAllowedMethod("readYaml", [Map.class], { c ->
      return c.get('text')
    })
  }
}
