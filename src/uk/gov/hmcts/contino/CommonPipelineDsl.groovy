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

  // void importServiceBusModules(String serviceBusName, String topicName, String subscriptionName, String resourceGroupName) {
  //   // if (serviceBusName?.trim() || topicName?.trim() || subscriptionName?.trim() || resourceGroupName?.trim() ) {
  //   //     throw new RuntimeException("Mandatory parameters not set for importServiceBusModules method.")
  //   // }   

  //   steps.env.IMPORT_SERVICE_BUS_MODULES = true
  //   steps.env.SERVICE_BUS_NAME = serviceBusName
  //   steps.env.TOPIC_NAME = topicName
  //   steps.env.SB_SUBSCRIPTION_NAME = subscriptionName
  //   steps.env.RESOURCE_GROUP_NAME = resourceGroupName
  // }

  void importServiceBusModules() {
    steps.env.IMPORT_SERVICE_BUS_MODULES = true
  }
}