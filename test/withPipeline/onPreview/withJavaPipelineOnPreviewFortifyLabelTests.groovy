package withPipeline.onPreview

import org.junit.Test
import withPipeline.BaseCnpPipelineTest

import static org.assertj.core.api.Assertions.assertThat

class withJavaPipelineOnPreviewFortifyLabelTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleJavaPipeline.jenkins"

  withJavaPipelineOnPreviewFortifyLabelTests() {
    super("PR-999", jenkinsFile)
  }

  @Test
  void 'enable_fortify_scan label runs the scan with the Jenkins FoD OAuth credential bound'() {
    def labels = ["status": 200, "content": '[{"id": 1, "name": "enable_fortify_scan"}]']
    def originalHttpRequest = helper.allowedMethodCallbacks.find { it.key.name == 'httpRequest' }.value
    helper.registerAllowedMethod("httpRequest", [LinkedHashMap.class], { Map m ->
      String url = m.get('url')?.toString()
      if (url?.startsWith("https://api.github.com/repos") && url?.endsWith("/labels")) {
        return labels
      }
      return originalHttpRequest.call(m)
    })
    helper.registerAllowedMethod("withCredentials", [List.class, Closure.class], { List ignored, Closure body -> body.call() })
    helper.registerAllowedMethod('retry', [LinkedHashMap.class, Closure.class], { Map ignored, Closure body -> body.call() })

    binding.getVariable('env').putAt('CHANGE_URL', 'http://github.com/some-repo/pr/16')
    binding.getVariable('env').putAt('CHANGE_TITLE', 'Some change')

    runScript("testResources/$jenkinsFile")

    // OAuth must be bound before the scan runner starts
    int vaultIndex = helper.callStack.findIndexOf { it.methodName == 'withFortifySecrets' }
    int scanIndex = helper.callStack.findIndexOf { it.methodName == 'fortifyOnDemandScan' }
    int credentialIndex = helper.callStack.findIndexOf {
      it.methodName == 'usernamePassword' && it.argsToString().contains('fortify-on-demand-oauth')
    }

    assertThat(vaultIndex).as('withFortifySecrets called').isNotNegative()
    assertThat(scanIndex).as('library FoD scan runner called').isGreaterThan(vaultIndex)
    assertThat(credentialIndex).as('fortify-on-demand-oauth credential bound before the scan starts')
      .isGreaterThan(vaultIndex).isLessThan(scanIndex)
  }
}
