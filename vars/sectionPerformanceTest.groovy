import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.PipelineCallbacks

def call(PipelineCallbacks pl, Builder builder) {

  stage("performanceTest") {
    pl.callAround('PerformanceTest') {
      timeout(time: pl.perfTestTimeout, unit: 'MINUTES') {
        builder.performanceTest()
      }
    }
  }

}

