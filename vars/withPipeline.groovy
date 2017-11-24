import uk.gov.hmcts.contino.*

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

  def pl = new PipelineCallbacks()

  body.delegate = pl
  body.call() // register callbacks

  node {
   platformSetup {
     withSubscription("jenkinsServicePrincipal", "infra-vault") {
        stage('Checkout') {
          deleteDir()
          checkout scm
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
        }
        else {
          echo "Sonarqube plugin not installed. Skipping static analysis."
        }
         }
       }

       stage('Deploy Dev') {
         pl.callAround('deploy:dev') {
           deployer.deploy('dev')
           deployer.healthCheck('dev')
         }
       }

       stage('Smoke Tests - Dev') {
         withEnv(["SMOKETEST_URL=${deployer.getServiceUrl('dev')}"]) {
           pl.callAround('smoketest:dev') {
             builder.smokeTest()
           }
         }
       }

       stage("OWASP") {

       }

        stage('Deploy Default') {
          pl.callAround('deploy:default') {
            deployer.deploy('default')
            deployer.healthCheck('default')
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
}
