import uk.gov.hmcts.contino.NightlyBuilder
import uk.gov.hmcts.contino.PipelineCallbacks

def call(PipelineCallbacks pl, NightlyBuilder builder) {

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

  try{
    stage("crossBrowserTest") {
      pl.callAround('crossBrowserTest') {
        timeout(time: 15, unit: 'MINUTES') {
          builder.crossBrowserTest()

        }
      }
    }
  }
  catch(err){
    echo err.printStackTrace()
  }

}

