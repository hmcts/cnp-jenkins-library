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
  stage("crossBrowser") {
    pl.callAround('build') {
      sauce('e0067992-049e-412c-9d15-2566a28cfb73') {
        sauceconnect(options: "--verbose --tunnel-identifier reformtunnel", verboseLogging: true)
        builder.crossBrowserTest()

      }
    }
  }
}
