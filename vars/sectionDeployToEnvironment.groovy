#!groovy
import uk.gov.hmcts.contino.Deployer

def call(Deployer deployer, String subscription, String environment, String product, String component) {
  stage("Build Infrastructure - ${environment}") {
    folderExists('infrastructure') {
      withSubscription(subscription) {
        dir('infrastructure') {
          withIlbIp(environment) {
            spinInfra("${product}-${component}", environment, false, subscription)
            scmServiceRegistration(environment)
          }
        }
      }
    }
  }

  stage("Deploy - ${environment} (staging slot)") {
    pl.callAround("deploy:${environment}") {
      deployer.deploy(environment)
      deployer.healthCheck(environment)
    }
  }

  stage("Smoke Test - ${environment} (staging slot)") {
    withEnv(["TEST_URL=${deployer.getServiceUrl(environment)}"]) {
      pl.callAround('smoketest:${environment}') {
        echo "Using TEST_URL: '$TEST_URL'"
        builder.smokeTest()
      }
    }
  }

  stage("Functional Test - ${environment} (staging slot)") {
    withEnv(["TEST_URL=${deployer.getServiceUrl(environment)}"]) {
      pl.callAround('functionaltest:${environment}') {
        echo "Using TEST_URL: '$TEST_URL'"
        builder.functionalTest()
      }
    }
  }

  stage("Promote - ${environment} (staging -> production slot)") {
    withSubscription(subscription) {
      sh "az webapp deployment slot swap --name \"${product}-${component}-${environment}\" --resource-group \"${product}-${component}-${environment}\" --slot staging --target-slot production"
    }
  }
}
