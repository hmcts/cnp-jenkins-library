package uk.gov.hmcts.contino

class CrossBrowserPipeline implements  PipelineType, Serializable {

  def steps
  NightlyBuilder nBuilder

  CrossBrowserPipeline(steps) {
      this.steps = steps
      nBuilder = new CrossBrowserPipelineSteps(steps)
  }

}
