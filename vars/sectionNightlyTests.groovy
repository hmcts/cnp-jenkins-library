import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.PipelineCallbacks
import uk.gov.hmcts.contino.PipelineType


def call(PipelineCallbacks pl, PipelineType pipelineType) {

  withTeamSecrets(pl) {
    Builder builder = pipelineType.builder

    stage('Checkout') {
      pl.callAround('checkout') {
        deleteDir()
        checkout scm
      }
    }

    stage("Build") {
      pl.callAround('build') {
        timeout(time: 15, unit: 'MINUTES') {
          builder.build()
        }
      }
    }

    try {
      stage('DependencyCheckNightly') {
        pl.callAround('DependencyCheckNightly') {
          timeout(time: 15, unit: 'MINUTES') {
            builder.securityCheck()
          }
        }
      }
    } catch (err) {
      err.printStackTrace()
      currentBuild.result = "UNSTABLE"
    }

    if (pl.crossBrowserTest) {
      try {
        stage("crossBrowserTest") {
          pl.callAround('crossBrowserTest') {
            timeout(time: pl.crossBrowserTestTimeout, unit: 'MINUTES') {
              builder.crossBrowserTest()
            }
          }
        }
      }
      catch (err) {
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

    if (pl.mutationTest) {
      try {
        stage('mutationTest') {
          pl.callAround('mutationTest') {
            timeout(time: pl.mutationTestTimeout, unit: 'MINUTES') {
              builder.mutationTest()
            }
          }
        }
      }
      catch (err) {
        err.printStackTrace()
        currentBuild.result = "UNSTABLE"
      }
    }

    if (pl.fullFunctionalTest) {
      try {
        stage('fullFunctionalTest') {
          pl.callAround('fullFunctionalTest') {
            timeout(time: pl.fullFunctionalTestTimeout, unit: 'MINUTES') {
              builder.fullFunctionalTest()
            }
          }
        }
      }
      catch (err) {
        err.printStackTrace()
        currentBuild.result = "UNSTABLE"
      }
    }

  }
}


def withTeamSecrets(PipelineCallbacks pl, Closure block) {
  def keyvaultUrl = null

  if (pl.vaultSecrets?.size() > 0) {
    if (pl.vaultName) {
      keyvaultUrl = "https://${pl.vaultName}.vault.azure.net/"
    } else {
      error "Please set vault name `setVaultName(${pl.vaultName})` if loading vault secrets"
    }
  }

  wrap([
    $class                   : 'AzureKeyVaultBuildWrapper',
    azureKeyVaultSecrets     : pl.vaultSecrets,
    keyVaultURLOverride      : keyvaultUrl,
    applicationIDOverride    : env.AZURE_CLIENT_ID,
    applicationSecretOverride: env.AZURE_CLIENT_SECRET
  ]) {
    block.call()
  }

}
