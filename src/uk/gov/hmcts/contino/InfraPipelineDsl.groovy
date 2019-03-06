package uk.gov.hmcts.contino

class InfraPipelineDsl extends CommonPipelineDsl implements Serializable {

  final InfraPipelineConfig config

  InfraPipelineDsl(PipelineCallbacksConfig callbacks, InfraPipelineConfig config) {
    super(callbacks, config)
    this.config = config
  }

}
