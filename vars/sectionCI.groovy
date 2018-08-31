import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.Deployer
import uk.gov.hmcts.contino.PipelineCallbacks
import uk.gov.hmcts.contino.PipelineType
import uk.gov.hmcts.contino.ProjectBranch
import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.azure.Acr

def testEnv(String testUrl, block) {
  def testEnvVariables = ["TEST_URL=${testUrl}"]

  withEnv(testEnvVariables) {
    echo "Using TEST_URL: '$env.TEST_URL'"
    block.call()
  }
}

def call(params) {
  PipelineCallbacks pl = params.pipelineCallbacks
  PipelineType pipelineType = params.pipelineType

  def subscription = params.subscription
  def product = params.product
  def component = params.component

  Builder builder = pipelineType.builder

  if (pl.dockerBuild) {
    withSubscription(subscription) {

      def registrySecrets = [
        [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'registry-name', version: '', envVariable: 'REGISTRY_NAME'],
        [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'registry-resource-group', version: '', envVariable: 'REGISTRY_RESOURCE_GROUP'],
        [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'aks-resource-group', version: '', envVariable: 'AKS_RESOURCE_GROUP'],
        [$class: 'AzureKeyVaultSecret', secretType: 'Secret', name: 'aks-cluster-name', version: '', envVariable: 'AKS_CLUSTER_NAME'],
      ]

      wrap([$class                   : 'AzureKeyVaultBuildWrapper',
            azureKeyVaultSecrets     : registrySecrets,
            keyVaultURLOverride      : env.INFRA_VAULT_URL,
            applicationIDOverride    : env.AZURE_CLIENT_ID,
            applicationSecretOverride: env.AZURE_CLIENT_SECRET
      ]) {

        def acr = new Acr(this, subscription, env.REGISTRY_NAME, env.REGISTRY_RESOURCE_GROUP)
        def dockerImage = new DockerImage(product, component, acr, new ProjectBranch(env.BRANCH_NAME).imageTag())

        stage('Docker Build') {
          pl.callAround('dockerbuild') {
            timeout(time: 15, unit: 'MINUTES') {
              acr.build(dockerImage)
            }
          }
        }
        def aksUrl
        if (pl.deployToAKS) {
          stage('Deploy to AKS') {
            pl.callAround('aksdeploy') {
              timeout(time: 15, unit: 'MINUTES') {
                aksUrl = aksDeploy(dockerImage, params, acr)
                log.info("deployed component URL: ${aksUrl}")
              }
            }
          }
          stage("Smoke Test - (staging slot)") {
            testEnv(aksUrl) {
              pl.callAround("smoketest:aks") {
                timeout(time: 10, unit: 'MINUTES') {
                  builder.smokeTest()
                }
              }
            }
          }

          def environment = subscription == 'nonprod' ? 'preview' : 'saat'

          onFunctionalTestEnvironment(environment) {
            stage("Functional Test - ${environment}") {
              testEnv(aksUrl) {
                pl.callAround("functionalTest:${environment}") {
                  timeout(time: 40, unit: 'MINUTES') {
                    builder.functionalTest()
                  }
                }
              }
            }
            if (pl.performanceTest) {
              stage("Performance Test - ${environment} (staging slot)") {
                testEnv(deployer.getServiceUrl(environment, "staging"), tfOutput) {
                  pl.callAround("performanceTest:${environment}") {
                    timeout(time: 120, unit: 'MINUTES') {
                      builder.performanceTest()
                      publishPerformanceReports(this, params)
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
