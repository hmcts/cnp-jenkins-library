
import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.Deployer


def call() {

  Builder builder = pipelineType.builder
  Deployer deployer = pipelineType.deployer


  def TEST_URL = deployer.getServiceUrl(environment, "staging")

  echo "Using TEST_URL....: ${env.TEST_URL}"

      stage('Checkout') {
        pl.callAround('checkout') {
          deleteDir()
          checkout scm
        }
      }

      if (pl.crossBrowserTest) {
        try {
          stage("Build") {
            pl.callAround('build') {
              timeout(time: 15, unit: 'MINUTES') {
                builder.build()
              }
            }
          }
          onFunctionalTestEnvironment(environment) {
            stage("crossBrowserTest") {
              testEnv(deployer.getServiceUrl(environment, "staging"), tfOutput) {
                echo "test url is ..... ${TEST_URL}"
                pl.callAround('crossBrowserTest') {
                  timeout(time: pl.crossBrowserTestTimeout, unit: 'MINUTES') {
                    builder.crossBrowserTest()
                  }
                }
              }
            }
          }
        } catch (err) {
          err.printStackTrace()
          currentBuild.result = "UNSTABLE"
        }
      }

      if (pl.performanceTest) {
        try {
          stage("performanceTest") {
            pl.callAround('PerformanceTest') {
              timeout(time: pl.perfTestTimeout, unit: 'MINUTES') {
                builder.performanceTest()
              }
            }
          }
        } catch (err) {
          err.printStackTrace()
          currentBuild.result = "UNSTABLE"
        }
      }
  }
