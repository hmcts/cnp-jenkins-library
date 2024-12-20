package withPipeline.onMaster

import groovy.mock.interceptor.StubFor
import org.junit.Before
import org.junit.Test
import uk.gov.hmcts.contino.GradleBuilder
import withPipeline.BaseCnpPipelineTest

class withJavaPipelineOnMasterTests extends BaseCnpPipelineTest {
  final static jenkinsFile = "exampleJavaPipeline.jenkins"

  withJavaPipelineOnMasterTests() {
    super("master", jenkinsFile)
    logDebug("withJavaPipelineOnMasterTests constructor")

  }
  @Before
  void setUp() {
    logDebug("withJavaPipelineOnMasterTests setUp")
    helper.libLoader.preloadLibraryClasses = false
    registerJavaPipelineAllowedMethods()
  }

  @Test
  void PipelineExecutesExpectedStepsInExpectedOrder() {
    def stubBuilder = new StubFor(GradleBuilder)
    stubBuilder.demand.with {
      setupToolVersion(1) {}
      build(1) {}
      test(1) {}
      securityCheck(1) {}
      sonarScan(1) {}
      smokeTest(1) {} //aat-staging
      functionalTest(1) {}
    }

    stubBuilder.use {
      runScript("testResources/$jenkinsFile")
    }

    stubBuilder.expect.verify()
  }

  @Test
  void PipelineExecutesExpectedStepsInExpectedOrderWithSkips() {
    helper.registerAllowedMethod("when", [boolean, Closure.class], {})

    def stubBuilder = new StubFor(GradleBuilder)
    stubBuilder.demand.with {
      setupToolVersion(1) {}
      build(0) {}
      test(0) {}
      securityCheck(0) {}
      sonarScan(0) {}
      smokeTest(1) {} //aat-staging
      functionalTest(1) {}
    }

    stubBuilder.use {
      runScript("testResources/$jenkinsFile")
    }

    stubBuilder.expect.verify()
  }
  void registerJavaPipelineAllowedMethods() {
    helper.registerAllowedMethod("retry", [Map.class, Closure.class], { Map args, Closure body ->
      int count = args.count
      for (int i = 0; i < count; i++) {
        try {
          body.call()
          break
        } catch (Exception e) {
          if (i == count - 1) {
            throw e
          }
        }
      }
    })
    helper.registerAllowedMethod("agent", [Closure.class], { Closure closure ->
      closure.call()
    })

    // Mock the node function
    helper.registerAllowedMethod("node", [String.class, Closure.class], { String agentType, Closure closure ->
      closure.call()
    })

    // Mock the timeoutWithMsg function
    helper.registerAllowedMethod("timeoutWithMsg", [Map.class, Closure.class], { Map args, Closure closure ->
      closure.call()
    })

    // Mock the dockerAgentSetup function
    helper.registerAllowedMethod("dockerAgentSetup", [], {})

    // Mock the sectionBuildAndTest function
    helper.registerAllowedMethod("sectionBuildAndTest", [Map.class], {})

    // Mock the helmPublish function
    helper.registerAllowedMethod("helmPublish", [Map.class], {})

    // Mock the sectionPromoteBuildToStage function
    helper.registerAllowedMethod("sectionPromoteBuildToStage", [Map.class], {})

    // Mock the notifyBuildFailure function
    helper.registerAllowedMethod("notifyBuildFailure", [Map.class], {})

    // Mock the notifyBuildFixed function
    helper.registerAllowedMethod("notifyBuildFixed", [Map.class], {})

    // Mock the notifyPipelineDeprecations function
    helper.registerAllowedMethod("notifyPipelineDeprecations", [Map.class], {})

    // Mock the deleteDir function
    helper.registerAllowedMethod("deleteDir", [], {})

    // Mock the metricsPublisher.publish function
    helper.registerAllowedMethod("metricsPublisher.publish", [String.class], {})

    // Mock the callbacksRunner.call function
    helper.registerAllowedMethod("callbacksRunner.call", [String.class], {})

    // Mock the onPR function
    helper.registerAllowedMethod("onPR", [Closure.class], { Closure closure ->
      closure.call()
    })

    // Mock the onMaster function
    helper.registerAllowedMethod("onMaster", [Closure.class], { Closure closure ->
      closure.call()
    })

    // Mock the onAutoDeployBranch function
    helper.registerAllowedMethod("onAutoDeployBranch", [Closure.class], { Closure closure ->
      closure.call()
    })

    // Mock the onPreview function
    helper.registerAllowedMethod("onPreview", [Closure.class], { Closure closure ->
      closure.call()
    })

    // Mock the sectionDeployToEnvironment function
    helper.registerAllowedMethod("sectionDeployToEnvironment", [Map.class], {})

    // Mock the sectionDeployToAKS function
    helper.registerAllowedMethod("sectionDeployToAKS", [Map.class], {})

    // Mock the highLevelDataSetup function
    helper.registerAllowedMethod("highLevelDataSetup", [Map.class], {})

    // Mock the sectionSyncBranchesWithMaster function
    helper.registerAllowedMethod("sectionSyncBranchesWithMaster", [Map.class], {})

    // Mock the stageWithAgent function
    helper.registerAllowedMethod("stageWithAgent", [String.class, String.class, Closure.class], { String stageName, String product, Closure closure ->
      closure.call()
    })

    // Mock the GithubAPI functions
    helper.registerAllowedMethod("githubApi.refreshPRCache", [], { "master" })
    helper.registerAllowedMethod("githubApi.refreshLabelCache", [], { [] })
    helper.registerAllowedMethod("githubApi.refreshTopicCache", [], { [] })
    helper.registerAllowedMethod("githubApi.checkForLabel", [String.class, String.class], { String branchName, String label -> false })
    helper.registerAllowedMethod("githubApi.checkForTopic", [String.class, String.class], { String branchName, String topic -> false })

    // Mock the ProjectBranch functions
    helper.registerAllowedMethod("new ProjectBranch", [String.class], { String branchName -> [isPreview: { false }] })

  }
}

