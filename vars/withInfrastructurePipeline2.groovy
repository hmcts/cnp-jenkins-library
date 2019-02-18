import uk.gov.hmcts.contino.InfraPipelineConfig
import uk.gov.hmcts.contino.InfraPipelineDsl

def call(String product, Closure body) {

  def pipelineConfig = new InfraPipelineConfig()

  def dsl = new InfraPipelineDsl(pipelineConfig)
  body.delegate = dsl
  body.call() // register pipeline config

  timestamps {
    node {

    }
  }
}
