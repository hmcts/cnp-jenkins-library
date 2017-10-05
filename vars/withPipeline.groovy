import uk.gov.hmcts.contino.*

def call(String type, String product, String app, Closure body) {

  def pipelineTypes = [
    java: new SpringBootPipelineType(this, product, app),
    nodejs: new NodePipelineType(this, product, app)
  ]

  def pipelineType = pipelineTypes.get(type)

  def deployer = pipelineType.deployer

  def builder = pipelineType.builder

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
      deployer.deploy('dev')
      deployer.healthCheck('dev')
    }

    stage('Smoke Tests - Dev'){
    }

    stage("OWASP") {

    }

    stage('Deploy Prod') {
      unstash product
      deployer.deploy('prod')
      deployer.healthCheck('prod')
    }

    stage('Smoke Tests - Prod'){
      withEnv(["SMOKETEST_URL=${deployer.getServiceUrl('prod')}"]){
        builder.smokeTest()
      }
    }

  }

}
