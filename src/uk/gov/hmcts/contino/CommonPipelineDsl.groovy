package uk.gov.hmcts.contino

abstract class CommonPipelineDsl implements Serializable {
  final PipelineCallbacksConfig callbacks
  final CommonPipelineConfig config

  CommonPipelineDsl(PipelineCallbacksConfig callbacks, CommonPipelineConfig config) {
    this.callbacks = callbacks
    this.config = config
  }
  
  void afterCheckout(Closure body) {
    after('checkout', body)
  }

  void before(String stage, Closure body) {
    callbacks.registerBefore(stage, body)
  }

  void after(String stage, Closure body) {
    callbacks.registerAfter(stage, body)
  }

  void onStageFailure(Closure body) {
    callbacks.registerOnStageFailure(body)
  }

  void onFailure(Closure body) {
    callbacks.registerOnFailure(body)
  }

  void onSuccess(Closure body) {
    callbacks.registerOnSuccess(body)
  }

  void enableSlackNotifications(String slackChannel) {
    config.slackChannel = slackChannel
  }

}
