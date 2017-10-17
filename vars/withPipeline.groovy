import uk.gov.hmcts.contino.*

def call(type, String product, String app, Closure body) {

  def pipelineTypes = [
    java: new SpringBootPipelineType(this, product, app),
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

  def pl = new Pipeline()

  body.delegate = pl
  body.call() // register callbacks

  node {

    stage('Checkout') {
      deleteDir()
      checkout scm

      if (pl.afterCheckoutBody != null) {
        pl.afterCheckoutBody.call()
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
