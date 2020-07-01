package withPipeline.onBranch

import com.lesfurets.jenkins.unit.BasePipelineTest
import groovy.mock.interceptor.MockFor
import groovy.mock.interceptor.StubFor
import org.junit.Test
import uk.gov.hmcts.contino.*
import withPipeline.BaseCnpPipelineTest

import static com.lesfurets.jenkins.unit.global.lib.LibraryConfiguration.library
import static uk.gov.hmcts.contino.ProjectSource.projectSource

class withNodeJsPipelineOnBranchTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleNodeJsPipeline.jenkins"

  withNodeJsPipelineOnBranchTests() {
    super("feature-branch", jenkinsFile)
  }

  @Test
  void PipelineExecutesExpectedSteps() {
    def stubBuilder = new StubFor(YarnBuilder)
    stubBuilder.demand.setupToolVersion() {}
    stubBuilder.demand.build() {}
    stubBuilder.demand.test() {}
    stubBuilder.demand.securityCheck() {}
    stubBuilder.demand.sonarScan() {}

    stubBuilder.use {
      runScript("testResources/$jenkinsFile")
    }

    stubBuilder.expect.verify()
  }
}

