package uk.gov.hmcts.contino

abstract class CommonPipelineDsl implements Serializable {
  final PipelineCallbacksConfig callbacks
  final CommonPipelineConfig config
  def final steps

  CommonPipelineDsl(Object steps, PipelineCallbacksConfig callbacks, CommonPipelineConfig config) {
    this.callbacks = callbacks
    this.config = config
    this.steps = steps
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
    steps.env.BUILD_NOTICE_SLACK_CHANNEL = slackChannel
  }

  void syncBranchesWithMaster(List<String> branches) {
    config.branchesToSyncWithMaster = branches
  }

  void importServiceBusModules() {
    steps.env.IMPORT_SERVICE_BUS_MODULES = true
  }
}