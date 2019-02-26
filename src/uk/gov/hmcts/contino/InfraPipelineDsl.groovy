package uk.gov.hmcts.contino

class InfraPipelineDsl extends CommonPipelineDsl implements Serializable {

  final InfraPipelineConfig config

  InfraPipelineDsl(PipelineCallbacksConfig callbacks, InfraPipelineConfig config) {
    super(callbacks)
    this.config = config
  }

  void enableSlackNotifications(String slackChannel) {
    config.slackChannel = slackChannel
  }

}
