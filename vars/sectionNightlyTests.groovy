
import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.PipelineCallbacks

def call(params) {

  PipelineCallbacks pl = params.pipelineCallbacks
  PipelineType pipelineType = params.pipelineType
  def subscription = params.subscription
  def environment = params.environment
  def product = params.product
  def component = params.component

  Builder builder = pipelineType.builder
  Deployer deployer = pipelineType.deployer


  withSubscription(subscription) {
    wrap([
      $class                   : 'AzureKeyVaultBuildWrapper',
      azureKeyVaultSecrets     : pl.vaultSecrets,
      keyVaultURLOverride      : tfOutput?.vaultUri?.value,
      applicationIDOverride    : env.AZURE_CLIENT_ID,
      applicationSecretOverride: env.AZURE_CLIENT_SECRET
    ]) {


      echo "TEST_URL is........" $ { deployer.getServiceUrl(environment, "staging") }

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
          stage("crossBrowserTest") {
            pl.callAround('crossBrowserTest') {
              timeout(time: pl.crossBrowserTestTimeout, unit: 'MINUTES') {
                builder.crossBrowserTest()
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
  }
}
