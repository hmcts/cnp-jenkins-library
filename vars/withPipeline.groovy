import uk.gov.hmcts.contino.AngularPipelineType
import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.Environment
import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.contino.NodePipelineType
import uk.gov.hmcts.contino.RubyPipelineType
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
import uk.gov.hmcts.contino.GithubAPI
import uk.gov.hmcts.pipeline.DeprecationConfig

def call(type, String product, String component, Closure body) {

  def branch = new ProjectBranch(env.BRANCH_NAME)

  def deploymentNamespace = branch.deploymentNamespace()
  def deploymentProduct = deploymentNamespace ? "$deploymentNamespace-$product" : product

  def pipelineTypes = [
    java  : new SpringBootPipelineType(this, product, component),
    nodejs: new NodePipelineType(this, product, component),
    angular: new AngularPipelineType(this, product, component),
    ruby: new RubyPipelineType(this, product, component)
  ]

  Subscription subscription = new Subscription(env)
  AKSSubscriptions aksSubscriptions = new AKSSubscriptions(this)

  def pipelineType

  if (type instanceof PipelineType) {
    pipelineType = type
  } else {
    pipelineType = pipelineTypes.get(type)
  }

  assert pipelineType != null

  MetricsPublisher metricsPublisher = new MetricsPublisher(this, currentBuild, product, component)
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

  def teamConfig = new TeamConfig(this).setTeamConfigEnv(product)
  String agentType = env.BUILD_AGENT_TYPE

  retry(conditions: [agent()], count: 2) {
    node(agentType) {
      timeoutWithMsg(time: 180, unit: 'MINUTES', action: 'pipeline') {
        def slackChannel = env.BUILD_NOTICES_SLACK_CHANNEL
        try {
          dockerAgentSetup()
          env.PATH = "$env.PATH:/usr/local/bin"

          sectionBuildAndTest(
            appPipelineConfig: pipelineConfig,
            pipelineCallbacksRunner: callbacksRunner,
            builder: pipelineType.builder,
            subscription: subscription.nonProdName,
            environment: environment.nonProdName,
            product: product,
            component: component
          )

          if (new ProjectBranch(env.BRANCH_NAME).isPreview()) {
            stage('Publish Helm chart') {
              helmPublish(
                appPipelineConfig: pipelineConfig,
                subscription: subscription.nonProdName,
                environment: environment.nonProdName,
                product: product,
                component: component
              )
            }

            sectionPromoteBuildToStage(
              appPipelineConfig: pipelineConfig,
              pipelineCallbacksRunner: callbacksRunner,
              pipelineType: pipelineType,
              subscription: subscription.nonProdName,
              product: product,
              component: component,
              stage: DockerImage.DeploymentStage.PREVIEW,
              environment: environment.nonProdName
            )
          }

          onPR {
            onTerraformChangeInPR {
              // we always need a tf plan of aat (i.e. staging)
              sectionDeployToEnvironment(
                appPipelineConfig: pipelineConfig,
                pipelineCallbacksRunner: callbacksRunner,
                pipelineType: pipelineType,
                subscription: subscription.nonProdName,
                aksSubscription: aksSubscriptions.aat,
                environment: environment.nonProdName,
                product: product,
                component: component,
                tfPlanOnly: true
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
                sectionDeployToEnvironment(
                  appPipelineConfig: pipelineConfig,
                  pipelineCallbacksRunner: callbacksRunner,
                  pipelineType: pipelineType,
                  subscription: subscription."${base_env_name}Name",
                  aksSubscription: aksSubscriptions."${base_env_name}",
                  environment: environment."${base_env_name}Name",
                  product: product,
                  component: component,
                  tfPlanOnly: true
                )
              } else {
                println "Skipping Terraform Plan against ${base_env_name} ... "
              }
            }

            sectionDeployToAKS(
              appPipelineConfig: pipelineConfig,
              pipelineCallbacksRunner: callbacksRunner,
              pipelineType: pipelineType,
              subscription: subscription.nonProdName,
              aksSubscription: aksSubscriptions.preview,
              environment: environment.previewName,
              product: product,
              component: component,
            )

          }

          onMaster {

            sectionDeployToEnvironment(
              appPipelineConfig: pipelineConfig,
              pipelineCallbacksRunner: callbacksRunner,
              pipelineType: pipelineType,
              subscription: subscription.nonProdName,
              aksSubscription: aksSubscriptions.aat,
              environment: environment.nonProdName,
              product: product,
              component: component,
              tfPlanOnly: false
            )

            highLevelDataSetup(
              appPipelineConfig: pipelineConfig,
              pipelineCallbacksRunner: callbacksRunner,
              builder: pipelineType.builder,
              environment: environment.nonProdName,
              product: product,
            )

            sectionDeployToAKS(
              appPipelineConfig: pipelineConfig,
              pipelineCallbacksRunner: callbacksRunner,
              pipelineType: pipelineType,
              subscription: subscription.nonProdName,
              aksSubscription: aksSubscriptions.aat,
              environment: environment.nonProdName,
              product: product,
              component: component,
            )

            stageWithAgent('Publish Helm chart', product) {
              helmPublish(
                appPipelineConfig: pipelineConfig,
                subscription: subscription.nonProdName,
                environment: environment.nonProdName,
                product: product,
                component: component
              )
            }

            sectionDeployToEnvironment(
              appPipelineConfig: pipelineConfig,
              pipelineCallbacksRunner: callbacksRunner,
              pipelineType: pipelineType,
              subscription: subscription.prodName,
              environment: environment.prodName,
              product: product,
              component: component,
              aksSubscription: aksSubscriptions.prod,
              tfPlanOnly: false
            )

            highLevelDataSetup(
              appPipelineConfig: pipelineConfig,
              pipelineCallbacksRunner: callbacksRunner,
              builder: pipelineType.builder,
              environment: environment.prodName,
              product: product,
            )

            sectionPromoteBuildToStage(
              appPipelineConfig: pipelineConfig,
              pipelineCallbacksRunner: callbacksRunner,
              pipelineType: pipelineType,
              subscription: subscription.nonProdName,
              product: product,
              component: component,
              stage: DockerImage.DeploymentStage.PROD,
              environment: environment.nonProdName
            )

            sectionSyncBranchesWithMaster(
              branchestoSync: pipelineConfig.branchesToSyncWithMaster,
              product: product
            )
          }

          onAutoDeployBranch { subscriptionName, environmentName, aksSubscription ->
            highLevelDataSetup(
              appPipelineConfig: pipelineConfig,
              pipelineCallbacksRunner: callbacksRunner,
              builder: pipelineType.builder,
              environment: environmentName,
              product: product,
            )

            sectionDeployToEnvironment(
              appPipelineConfig: pipelineConfig,
              pipelineCallbacksRunner: callbacksRunner,
              pipelineType: pipelineType,
              subscription: subscriptionName,
              environment: environmentName,
              product: product,
              component: component,
              aksSubscription: aksSubscription,
              tfPlanOnly: false
            )

          // Performance Test Pipeline: Setup -> Parallel Testing
          if (pipelineConfig.performanceTestStages || pipelineConfig.gatlingLoadTests) {

            // Set aksUrl from TEST_URL for performance test stages
            def aksUrl = env.TEST_URL

            // Helper method to set test environment variables (only used within performance stages)
            def testEnv = { String testUrl, block ->
              def testEnvName = new Environment(env).nonProdName
              def testEnvVariables = ["TEST_URL=${testUrl}","ENVIRONMENT_NAME=${testEnvName}"]

              withEnv(testEnvVariables) {
                echo "Using TEST_URL: ${env.TEST_URL}"
                echo "Using ENVIRONMENT_NAME: ${env.ENVIRONMENT_NAME}"
                block.call()
              }
            }

            // Load performance test secrets once for all stages
            def perfKeyVaultUrl = "https://rpe-shared-perftest.vault.azure.net/" //https://et-perftest.vault.azure.net/
            def perfSecrets = [
              [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'perf-synthetic-monitor-token', version: '', envVariable: 'PERF_SYNTHETIC_MONITOR_TOKEN'],
              [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'perf-metrics-token', version: '', envVariable: 'PERF_METRICS_TOKEN'],
              [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'perf-event-token', version: '', envVariable: 'PERF_EVENT_TOKEN'],
              [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'perf-synthetic-update-token', version: '', envVariable: 'PERF_SYNTHETIC_UPDATE_TOKEN']
            ]
            
            withAzureKeyvault(
              azureKeyVaultSecrets: perfSecrets,
              keyVaultURLOverride: perfKeyVaultUrl
            ) {
            
              // Stage 1: Dynatrace Setup - Post build info, events, and metrics first
              if (pipelineConfig.performanceTestStages) {
                stageWithAgent("Dynatrace Performance Setup - ${environment}", product) {
                  testEnv(aksUrl) {
                    def success = true
                    try {
                      pcr.callAround("dynatracePerformanceSetup:${environment}") {
                        timeoutWithMsg(time: 5, unit: 'MINUTES', action: "Dynatrace Performance Setup - ${environment}") {
                          dynatracePerformanceSetup([
                            product: product,
                            component: component,
                            environment: environment,
                            testUrl: env.TEST_URL,
                            secrets: pipelineConfig.vaultSecrets,
                            configPath: pipelineConfig.performanceTestConfigPath
                          ])
                        }
                      }
                    } catch (err) {
                      success = false
                      echo "Dynatrace setup failed: ${err.message}"
                      // Don't fail the build for setup issues, continue with tests
                    } finally {
                      //savePodsLogs(dockerImage, params, "dynatrace-setup")
                    }
                  }
                }
              }
            
            // Stage 2: Run performance tests in parallel (if both enabled) or sequential (if only one enabled)
            def testStages = [:]
            
            if (pipelineConfig.performanceTestStages) {
              testStages['Dynatrace Synthetic Tests'] = {
                stageWithAgent("Dynatrace Synthetic Tests - ${environment}", product) {
                  testEnv(aksUrl) {
                    def success = true
                    try {
                      pcr.callAround("dynatraceSyntheticTest:${environment}") {
                        timeoutWithMsg(time: pipelineConfig.performanceTestStagesTimeout, unit: 'MINUTES', action: "Dynatrace Synthetic Tests - ${environment}") {
                          dynatraceSyntheticTest([
                            product: product,
                            component: component,
                            environment: environment,
                            testUrl: env.TEST_URL,
                            secrets: pipelineConfig.vaultSecrets,
                            configPath: pipelineConfig.performanceTestConfigPath,
                            performanceTestStagesEnabled: pipelineConfig.performanceTestStages,
                            gatlingLoadTestsEnabled: pipelineConfig.gatlingLoadTests
                          ])
                        }
                      }
                    } catch (err) {
                      success = false
                      throw err
                    } finally {
                      savePodsLogs(dockerImage, params, "dynatrace-synthetic")
                      if (!success) {
                        clearHelmReleaseForFailure(enableHelmLabel, pipelineConfig, dockerImage, params, pcr)
                      }
                    }
                  }
                }
              }
            }
            
            if (pipelineConfig.gatlingLoadTests) {
              testStages['Gatling Load Tests'] = {
                stageWithAgent("Gatling Load Tests - ${environment}", product) {
                  testEnv(aksUrl) {
                    def success = true
                    try {
                      pcr.callAround("gatlingLoadTests:${environment}") {
                        timeoutWithMsg(time: pipelineConfig.gatlingLoadTestTimeout, unit: 'MINUTES', action: "Gatling Load Tests - ${environment}") {
                          gatlingExternalLoadTest([
                            product: product,
                            component: component,
                            environment: environment,
                            subscription: subscription,
                            gatlingRepo: pipelineConfig.gatlingRepo,
                            gatlingBranch: pipelineConfig.gatlingBranch,
                            gatlingSimulation: pipelineConfig.gatlingSimulation
                          ])
                        }
                      }
                    } catch (err) {
                      success = false
                      throw err
                    } finally {
                      savePodsLogs(dockerImage, params, "gatling-load-tests")
                      if (!success) {
                        clearHelmReleaseForFailure(enableHelmLabel, pipelineConfig, dockerImage, params, pcr)
                      }
                    }
                  }
                }
              }
            }
            
              // Execute test stages
              if (testStages.size() > 1) {
                echo "Running Dynatrace Synthetic Tests and Gatling Load Tests in parallel..."
                parallel(testStages)
              } else {
                echo "Running single performance test stage..."
                testStages.values().first().call()
              }

              // Stage 3: Site Reliability Guardian Evaluation (if enabled)
              if (pipelineConfig.srgEvaluation) {
                stageWithAgent("Site Reliability Guardian Evaluation - ${environment}", product) {
                  testEnv(aksUrl) {
                    try {
                      pcr.callAround("srgEvaluation:${environment}") {
                        evaluateDynatraceSRG([
                          environment: environment,
                          srgServiceName: pipelineConfig.srgServiceName,
                          performanceTestStartTime: env.PERF_TEST_START_TIME,
                          performanceTestEndTime: env.PERF_TEST_END_TIME,
                          gatlingTestStartTime: env.GATLING_TEST_START_TIME,
                          gatlingTestEndTime: env.GATLING_TEST_END_TIME,
                          srgFailureBehavior: pipelineConfig.srgFailureBehavior,
                          product: product,
                          component: component
                        ])
                      }
                    } catch (Exception e) {
                      echo "SRG evaluation stage failed: ${e.message}"
                      if (pipelineConfig.srgFailureBehavior == 'fail') {
                        clearHelmReleaseForFailure(enableHelmLabel, pipelineConfig, dockerImage, params, pcr)
                        throw e
                      }
                    }
                  }
                }
              }
              
            } // End withAzureKeyvault block
          } // End performance Block
          }

          onPreview {
            sectionDeployToEnvironment(
              appPipelineConfig: pipelineConfig,
              pipelineCallbacksRunner: callbacksRunner,
              pipelineType: pipelineType,
              subscription: subscription.previewName,
              environment: environment.previewName,
              product: deploymentProduct,
              component: component,
              aksSubscription: aksSubscriptions.preview,
              tfPlanOnly: false
            )
          }
        } catch (err) {
          if (err.message != null && err.message.startsWith('AUTO_ABORT')) {
            currentBuild.result = 'ABORTED'
            metricsPublisher.publish(err.message)
            return
          } else {
            currentBuild.result = "FAILURE"
            notifyBuildFailure channel: slackChannel
            metricsPublisher.publish('Pipeline Failed')
          }
          callbacksRunner.call('onFailure')
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
  }
}
