package uk.gov.hmcts.contino

class InfraPipelineDsl extends CommonPipelineDsl implements Serializable {

  final InfraPipelineConfig config
  def final steps

  InfraPipelineDsl(Object steps, PipelineCallbacksConfig callbacks, InfraPipelineConfig config) {
    super(steps, callbacks, config)
    this.config = config
    this.steps = steps
  }

  void planOnly() {
    config.planOnly = true
  }

}
