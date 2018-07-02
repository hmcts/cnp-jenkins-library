import uk.gov.hmcts.contino.NightlyBuilder
import uk.gov.hmcts.contino.PipelineCallbacks

def call(PipelineCallbacks pl, NightlyBuilder builder) {

  stage("performanceTest") {
    pl.callAround('PerformanceTest') {
      timeout(time: pl.perfTestTimeout, unit: 'MINUTES') {
        builder.performanceTest()
      }
    }
  }

}

