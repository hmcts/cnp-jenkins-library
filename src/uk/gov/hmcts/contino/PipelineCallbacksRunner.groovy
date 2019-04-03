package uk.gov.hmcts.contino

import org.jenkinsci.plugins.pipeline.modeldefinition.Utils

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
      nullSafeCall('after:all')
    }
  }

  void callAround(String stage, boolean condition, Closure body) {
    if (condition) {
      callAround(stage, body)
    } else {
      Utils.markStageSkippedForConditional(stage)
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
