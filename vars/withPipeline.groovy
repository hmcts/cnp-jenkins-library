import uk.gov.hmcts.contino.*

def call(steps, String type, String product, Closure body) {

  def builders = [java: new GradleBuilder(steps)]

  def deployers = [java: new WebAppDeploy(steps, product, "recipe-backend")]

  def deployer = deployers.get(type)

  def builder = builders.get(type)

  node {

    stage('Checkout') {
      deleteDir()
      checkout scm
    }
    stage("Build") {
      builder.build()
      stash name: product, includes: "build/libs/*.jar"
    }

    stage("Test") {
      builder.test()
    }
    stage("Sonar Scan"){

    }
    stage("Security Checks" ){
      stage("NSP") {
      }

    }
    stage('Deploy Dev') {
      unstash product
      deployer.deployJavaWebApp('dev', 'build/libs/moj-rhubarb-recipes-service-0.0.1.jar', 'web.config')
      deployer.healthCheck('dev')
    }

    stage('Smoke Tests - Dev'){
    }
    stage("OWASP") {

    }

    stage('Deploy Prod') {
      unstash product
      deployer.deployJavaWebApp('prod', 'build/libs/moj-rhubarb-recipes-service-0.0.1.jar', 'web.config')
      deployer.healthCheck('prod')
    }
    stage('Smoke Tests - Prod'){
    }


  }

}
