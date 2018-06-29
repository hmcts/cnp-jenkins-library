package uk.gov.hmcts.contino

class NightlyPipeline implements  PipelineType, Serializable {

  def steps
  NightlyBuilder nBuilder

  NightlyPipeline(steps) {
      this.steps = steps
      nBuilder = new NightlyPipelineSteps(steps)
  }

}
