import uk.gov.hmcts.contino.InfraPipelineConfig
import uk.gov.hmcts.contino.InfraPipelineDsl
import uk.gov.hmcts.contino.PipelineCallbacksConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.contino.Subscription
import uk.gov.hmcts.contino.Environment
import uk.gov.hmcts.pipeline.TeamConfig
import uk.gov.hmcts.contino.GithubAPI
import uk.gov.hmcts.contino.ProjectBranch

def call(String product, String component = null, Closure body) {

  Subscription subscription = new Subscription(env)
  Environment environment = new Environment(env)
  MetricsPublisher metricsPublisher = new MetricsPublisher(this, currentBuild, product, "")

  def pipelineConfig = new InfraPipelineConfig()
  def callbacks = new PipelineCallbacksConfig()
  def callbacksRunner = new PipelineCallbacksRunner(callbacks)
  def ProjectBranch branch = new ProjectBranch(env.BRANCH_NAME)

  callbacks.registerAfterAll { stage ->
    metricsPublisher.publish(stage)
  }

  def dsl = new InfraPipelineDsl(this, callbacks, pipelineConfig)
  body.delegate = dsl
  body.call() // register pipeline config

  def teamConfig = new TeamConfig(this).setTeamConfigEnv(product)
  String agentType = env.BUILD_AGENT_TYPE

  node(agentType) {
    def slackChannel = env.BUILD_NOTICES_SLACK_CHANNEL
    try {
      dockerAgentSetup()
      env.PATH = "$env.PATH:/usr/local/bin"

      stageWithAgent('Checkout', product) {
        checkoutScm(pipelineCallbacksRunner: callbacksRunner)
      }

      onMaster {
        sectionInfraBuild(
          subscription: subscription.nonProdName,
          environment: environment.nonProdName,
          product: product,
          component: component,
          pipelineCallbacksRunner: callbacksRunner,
        )

        sectionInfraBuild(
          subscription: subscription.prodName,
          environment: environment.prodName,
          product: product,
          component: component,
          pipelineCallbacksRunner: callbacksRunner,
        )

        sectionSyncBranchesWithMaster(
          branchestoSync: pipelineConfig.branchesToSyncWithMaster,
          product: product
        )
      }

      onAutoDeployBranch { subscriptionName, environmentName, aksSubscription ->
        sectionInfraBuild(
          subscription: subscriptionName,
          environment: environmentName,
          product: product,
          component: component,
          pipelineCallbacksRunner: callbacksRunner,
        )
      }

      onPR {
        sectionInfraBuild(
          subscription: subscription.nonProdName,
          environment: environment.nonProdName,
          product: product,
          planOnly: true,
          component: component,
          expires: pipelineConfig.expiryDate,
          pipelineCallbacksRunner: callbacksRunner,
        )

        final String LABEL_NO_TF_PLAN_ON_PROD = "not-plan-on-prod"
        def githubApi = new GithubAPI(this)
        def targetBranch = githubApi.refreshPRCache() // e.g. demo, perftest, ithc, master, or non-standards
        def labelsCache = githubApi.refreshLabelCache()
        def topicsCache = githubApi.refreshTopicCache()
        def branchName = branch.branchName // e.g. PR-123
        def base_envs = ["demo", "perftest", "ithc"]

        println "labelsCache: ${labelsCache} \ntopicsCache: ${topicsCache}"
        // check if the PR has the label not-plan-on-prod
        boolean optOutTfPlanOnProdFound = githubApi.checkForLabel(branchName, LABEL_NO_TF_PLAN_ON_PROD)
        // check if the PR has the topic 'not-plan-on-prod' if it can not find the label `not-plan-on-prod`
        if (!optOutTfPlanOnProdFound) {
          optOutTfPlanOnProdFound = githubApi.checkForTopic(LABEL_NO_TF_PLAN_ON_PROD)
        }
        println "optOutTfPlanOnProdFound: " + optOutTfPlanOnProdFound.toString()

        // set the base environment to prod if the target branch is not in the list of base_envs
        // todo: need to find out if we need to deal with branches 'preview' and 'aat' for AksSubscriptions
        def base_env_name = targetBranch
        if (!base_envs.contains(targetBranch)) {
          base_env_name = "prod"
        }

        println "${branchName} being merged into: ${targetBranch}" + " base_env_name: " + base_env_name

        // deploy to environment, and run terraform plan against prod if the label/topic LABEL_NO_TF_PLAN_ON_PROD not found
        if (!optOutTfPlanOnProdFound) {
          println "Apply Terraform Plan against ${base_env_name}"
          sectionInfraBuild(
            subscription: subscription."${base_env_name}Name",
            environment: environment."${base_env_name}Name",
            product: product,
            planOnly: true,
            component: component,
            expires: pipelineConfig.expiryDate,
            pipelineCallbacksRunner: callbacksRunner,
          )
        } else {
          println "Skipping Terraform Plan against ${base_env_name} ... "
        }
      }
    } catch (err) {
      currentBuild.result = "FAILURE"
      notifyBuildFailure channel: slackChannel

      callbacksRunner.call('onFailure')
      metricsPublisher.publish('Pipeline Failed')
      throw err
    } finally {
      notifyPipelineDeprecations(slackChannel, metricsPublisher)
      if (env.KEEP_DIR_FOR_DEBUGGING != "true") {
        deleteDir()
      }
    }

    notifyBuildFixed channel: slackChannel

    callbacksRunner.call('onSuccess')
    metricsPublisher.publish('Pipeline Succeeded')
  }
}
