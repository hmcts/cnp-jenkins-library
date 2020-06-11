import uk.gov.hmcts.contino.AngularPipelineType
import uk.gov.hmcts.contino.Environment
import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.contino.NodePipelineType
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.contino.SpringBootPipelineType
import uk.gov.hmcts.contino.Subscription
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.AppPipelineDsl
import uk.gov.hmcts.contino.PipelineCallbacksConfig
import uk.gov.hmcts.contino.PipelineCallbacksRunner
import uk.gov.hmcts.pipeline.AKSSubscriptions
import uk.gov.hmcts.pipeline.TeamConfig

def call(type, String product, String component, Closure body) {

  def branch = new ProjectBranch(env.BRANCH_NAME)

  def deploymentNamespace = branch.deploymentNamespace()
  def deploymentProduct = deploymentNamespace ? "$deploymentNamespace-$product" : product

  def pipelineTypes = [
    java  : new SpringBootPipelineType(this, deploymentProduct, component),
    nodejs: new NodePipelineType(this, deploymentProduct, component),
    angular: new AngularPipelineType(this, deploymentProduct, component)
  ]

  Subscription subscription = new Subscription(env)
  AKSSubscriptions aksSubscriptions = new AKSSubscriptions(this)

  PipelineType pipelineType

  if (type instanceof PipelineType) {
    pipelineType = type
  } else {
    pipelineType = pipelineTypes.get(type)
  }

  assert pipelineType != null

  MetricsPublisher metricsPublisher = new MetricsPublisher(this, currentBuild, product, component, subscription.prodName )
  def pipelineConfig = new AppPipelineConfig()
  def callbacks = new PipelineCallbacksConfig()
  def callbacksRunner = new PipelineCallbacksRunner(callbacks)

  callbacks.registerAfterAll { stage ->
    metricsPublisher.publish(stage)
  }

  def dsl = new AppPipelineDsl(this, callbacks, pipelineConfig)
  body.delegate = dsl
  body.call() // register pipeline config

  dsl.onStageFailure() {
    currentBuild.result = "FAILURE"
  }

  Environment environment = new Environment(env)

  node {
    def slackChannel = new TeamConfig(this).getBuildNoticesSlackChannel(product)
    try {
      env.PATH = "$env.PATH:/usr/local/bin"



      onPR {
      }

      onMaster {

        PipelineCallbacksRunner pcr = callbacksRunner
        AppPipelineConfig config = pipelineConfig
        Builder builder = pipelineType.builder

        def azSubscription = subscription.nonProdName
        def pactBrokerUrl = environment.pactBrokerUrl
        def acr
        def dockerImage
        def projectBranch
        boolean noSkipImgBuild = true

        stage('Checkout') {
          pcr.callAround('checkout') {
            checkoutScm()
            withAcrClient(azSubscription) {
              projectBranch = new ProjectBranch(env.BRANCH_NAME)
              acr = new Acr(this, azSubscription, env.REGISTRY_NAME, env.REGISTRY_RESOURCE_GROUP, env.REGISTRY_SUBSCRIPTION)
              dockerImage = new DockerImage(product, component, acr, projectBranch.imageTag(), env.GIT_COMMIT)
              noSkipImgBuild = env.NO_SKIP_IMG_BUILD?.trim()?.toLowerCase() == 'true' || !acr.hasTag(dockerImage)
            }
          }
        }


        stage("Build") {
          onPR {
            enforceChartVersionBumped product: product, component: component
            warnAboutAADIdentityPreviewHack product: product, component: component
          }

          builder.setupToolVersion()

          if (!fileExists('Dockerfile')) {
            WarningCollector.addPipelineWarning("deprecated_no_dockerfile", "A Dockerfile will be required for all app builds. Docker builds (enableDockerBuild()) wil be enabled by default. ", new Date().parse("dd.MM.yyyy", "17.12.2019"))
          }

          // always build master and demo as we currently do not deploy an image there
          boolean envSub = autoDeployEnvironment() != null
          when(noSkipImgBuild || projectBranch.isMaster() || envSub) {
            pcr.callAround('build') {
              timeoutWithMsg(time: 15, unit: 'MINUTES', action: 'build') {
                builder.build()
              }
            }
          }

        }

        stage("Tests") {

          when (noSkipImgBuild) {
            parallel(

              "Unit tests": {
                pcr.callAround('test') {
                  timeoutWithMsg(time: 20, unit: 'MINUTES', action: 'test') {
                    builder.test()
                  }
                }
              },

              failFast: true
            )
          }
        }

        if (config.pactBrokerEnabled) {
          stage("Pact Consumer Verification") {
            def version = env.GIT_COMMIT.length() > 7 ? env.GIT_COMMIT.substring(0, 7) : env.GIT_COMMIT
            def isOnMaster = new ProjectBranch(env.BRANCH_NAME).isMaster()

            env.PACT_BRANCH_NAME = isOnMaster ? env.BRANCH_NAME : env.CHANGE_BRANCH
            env.PACT_BROKER_URL = pactBrokerUrl

            /*
             * These instructions have to be kept in order
             */

            if (config.pactConsumerTestsEnabled) {
              pcr.callAround('pact-consumer-tests') {
                builder.runConsumerTests(pactBrokerUrl, version)
              }
            }
          }
        }

      }

      onAutoDeployBranch { subscriptionName, environmentName, aksSubscription ->
      }

      onPreview {
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
