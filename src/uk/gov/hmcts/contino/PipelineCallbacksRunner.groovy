package uk.gov.hmcts.contino

class PipelineCallbacksRunner implements Serializable {
  final PipelineCallbacksConfig config

  PipelineCallbacksRunner(PipelineCallbacksConfig config) {
    this.config = config
  }

  void callAfter(String stage) {
    nullSafeCall('after:' + stage, stage)
  }

  void callBefore(String stage) {
    nullSafeCall('before:' + stage, stage)
  }

  void callAround(String stage, Closure body) {
    callBefore(stage)
    try {
      body.call()
    } catch (err) {
      call('onStageFailure', stage)
      throw err
    } finally {
      callAfter(stage)
      nullSafeCall('after:all', stage)
    }
  }

  void callAround(String stage, boolean condition, Closure body) {
    if (condition) {
      callAround(stage, body)
    } else {
      echo "Stage ${stage} skipped"
    }
  }

  void call(String callback, String stage = null) {
    nullSafeCall(callback, stage)
  }

  private def nullSafeCall(String key, String stage) {
    def body = config.bodies.get(key)
    if (body != null) {
      body.call(stage)
    }
  }
}
