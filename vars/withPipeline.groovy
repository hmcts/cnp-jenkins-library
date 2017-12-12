import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.Deployer
import uk.gov.hmcts.contino.NodePipelineType
import uk.gov.hmcts.contino.PipelineCallbacks
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.SpringBootPipelineType
import uk.gov.hmcts.contino.MetricsPublisher

def call(type, String product, String app, Closure body) {
  def pipelineTypes = [
    java  : new SpringBootPipelineType(this, product, app),
    nodejs: new NodePipelineType(this, product, app)
  ]

  PipelineType pipelineType

  if (type instanceof PipelineType) {
    pipelineType = type
  } else {
    pipelineType = pipelineTypes.get(type)
  }

  assert pipelineType != null

  Deployer deployer = pipelineType.deployer
  Builder builder = pipelineType.builder

  MetricsPublisher metricsPublisher = new MetricsPublisher(this, currentBuild)

  def pl = new PipelineCallbacks()

  body.delegate = pl
  body.call() // register callbacks

  try {
    node {
      platformSetup {
        stage('Checkout') {
          pl.callAround('checkout') {
            deleteDir()
            checkout scm
          }
        }

        stage("Build") {
          pl.callAround('build') {
            builder.build()
          }
        }

        stage("Test") {
          pl.callAround('test') {
            builder.test()
          }
        }

        stage("Security Checks") {
          pl.callAround('securitychecks') {
            builder.securityCheck()
          }
        }

        stage("Sonar Scan") {
          pl.callAround('sonarscan') {
            if (Jenkins.instance.getPluginManager().getPlugins().find { it.getShortName() == 'sonar' } != null) {
              withSonarQubeEnv("SonarQube") {
                builder.sonarScan();
              }

              timeout(time: 1, unit: 'MINUTES') {
                def qg = steps.waitForQualityGate()
                if (qg.status != 'OK') {
                  error "Pipeline aborted due to quality gate failure: ${qg.status}"
                }
              }
            } else {
              echo "Sonarqube plugin not installed. Skipping static analysis."
            }
          }
        }

        onMaster {
/*
          folderExists('infrastructure') {
            terraform.ini("${product}-${app}", this)
            withSubscription('nonprod') {
              dir('infrastructure') {
                lock("${product}-${app}-nonprod") {
                  stage('getIlbIp') {
                    def envSuffix = 'nonprod'
                    def response = httpRequest httpMode: 'POST', requestBody: "grant_type=client_credentials&resource=https%3A%2F%2Fmanagement.core.windows.net%2F&client_id=$ARM_CLIENT_ID&client_secret=$ARM_CLIENT_SECRET", acceptType: 'APPLICATION_JSON', url: "https://login.microsoftonline.com/$ARM_TENANT_ID/oauth2/token"
                    TOKEN = new JsonSlurperClassic().parseText(response.content).access_token
                    def vip = httpRequest httpMode: 'GET', customHeaders: [[name: 'Authorization', value: "Bearer ${TOKEN}"]], url: "https://management.azure.com/subscriptions/$ARM_SUBSCRIPTION_ID/resourceGroups/core-infra-$envSuffix/providers/Microsoft.Web/hostingEnvironments/core-compute-$envSuffix/capacities/virtualip?api-version=2016-09-01"
                    def internalip = new JsonSlurperClassic().parseText(vip.content).internalIpAddress
                    println internalip
                    env.TF_VAR_ilbIp = internalip
                  }
                  stage('Infrastructure Plan - nonprod') {
                    terraform.plan('nonprod')
                  }
                  stage('Infrastructure Build - nonprod') {
                    terraform.apply('nonprod')
                  }
                }
              }
            }
          }

          stage('Deploy nonprod') {
            pl.callAround('deploy:nonprod') {
              deployer.deploy('nonprod')
              deployer.healthCheck('nonprod')
            }
          }

          stage('Smoke Tests - nonprod') {
            withEnv(["SMOKETEST_URL=${deployer.getServiceUrl('nonprod')}"]) {
              pl.callAround('smoketest:nonprod') {
                builder.smokeTest()
              }
            }
          }
*/
          stage("OWASP") {

          }

          folderExists('infrastructure') {
            terraform.ini("${product}-${app}", this)
            withSubscription('prod') {
              dir('infrastructure') {
                lock("${product}-${app}-prod") {
                  withIlbIp('prod') {
                    stage('Infrastructure Plan - prod') {
                      terraform.plan('prod')
                    }
                    stage('Infrastructure Build - prod') {
                      terraform.apply('prod')
                    }
                  }
                }
              }
            }
          }

          stage('Deploy Prod') {
            pl.callAround('deploy:prod') {
              deployer.deploy('prod')
              deployer.healthCheck('prod')
            }
          }

          stage('Smoke Tests - Prod') {
            withEnv(["SMOKETEST_URL=${deployer.getServiceUrl('prod')}"]) {
              pl.callAround('smoketest:prod') {
                builder.smokeTest()
              }
            }
          }
        }
      }
    }
  } catch (err) {
    if (pl.slackChannel) {
      notifyBuildFailure channel: pl.slackChannel
    }

    pl.call('onFailure')
    withCredentials([string(credentialsId: 'COSMOSDB_TOKEN_KEY', variable: 'COSMOSDB_TOKEN_KEY')]) {
      metricsPublisher.publish()
    }
    throw err
  }

  node {
    if (pl.slackChannel) {
      notifyBuildFixed channel: pl.slackChannel
    }

    pl.call('onSuccess')
    withCredentials([string(credentialsId: 'COSMOSDB_TOKEN_KEY', variable: 'COSMOSDB_TOKEN_KEY')]) {
      metricsPublisher.publish()
    }
  }
}
