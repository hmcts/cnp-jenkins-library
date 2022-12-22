package uk.gov.hmcts.contino

class InfraPipelineDsl extends CommonPipelineDsl implements Serializable {

  final InfraPipelineConfig config
  def final steps

  InfraPipelineDsl(Object steps, PipelineCallbacksConfig callbacks, InfraPipelineConfig config) {
    super(steps, callbacks, config)
    this.config = config
    this.steps = steps
  }

  void expiresAfter(String expiresAfter) {
    config.expiresAfter = expiresAfter
    steps.env.EXPIRES_AFTER = expiresAfter
  }

}
