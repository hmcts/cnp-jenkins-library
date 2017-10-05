import uk.gov.hmcts.contino.*

def call(String type, String product, String app, Closure body) {

  def pipelineTypes = [
    java: new SpringBootPipelineType(this, product, app),
    nodejs: new NodePipelineType(this, product, app)
  ]

  def pipelineType = pipelineTypes.get(type)

  def deployer = pipelineType.deployer

  def builder = pipelineType.builder

  def pl = new Pipeline()

  body.call(pl)

  node {

    stage('Checkout') {
      deleteDir()
      checkout scm
      if (pl.afterCheckout) {
        pl.afterCheckout.call()
      }
    }

    stage("Build") {
      builder.build()
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
      deployer.deploy('dev')
      deployer.healthCheck('dev')
    }

    stage('Smoke Tests - Dev'){
    }

    stage("OWASP") {

    }

    stage('Deploy Prod') {
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
