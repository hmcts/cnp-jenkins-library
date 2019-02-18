package uk.gov.hmcts.contino

class PipelineCallbacksRunner implements Serializable {
  final PipelineCallbacksConfig config

  PipelineCallbacksRunner(PipelineCallbacksConfig config) {
    this.config = config
  }

  void callAfter(String stage) {
    nullSafeCall('after:' + stage)
  }

  void callBefore(String stage) {
    nullSafeCall('before:' + stage)
  }

  void callAround(String stage, Closure body) {
    callBefore(stage)
    try {
      body.call()
    } catch (err) {
      call('onStageFailure')
      throw err
    } finally {
      callAfter(stage)
    }
  }

  void call(String callback) {
    nullSafeCall(callback)
  }

  private def nullSafeCall(String key) {
    def body = config.bodies.get(key)
    if (body != null) {
      body.call()
    }
  }
}
