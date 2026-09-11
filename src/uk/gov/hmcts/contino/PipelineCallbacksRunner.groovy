package uk.gov.hmcts.contino

import uk.gov.hmcts.pipeline.deprecation.WarningCollector

import java.time.LocalDate

class PipelineCallbacksRunner implements Serializable {
  def final config

  PipelineCallbacksRunner(config) {
    this.config = config
  }

  private def callAfter(String stage) {
    if (config.bodies.containsKey('after:' + stage)) {
      WarningCollector.addPipelineWarning("deprecated_after", "after(${stage}) is deprecated, consider using 'afterSuccess', 'afterFailure', 'afterAlways' instead", LocalDate.of(2023, 1, 30))
    }
    nullSafeCall('after:' + stage, stage)
  }

  void callAfterSuccess(String stage) {
    nullSafeCall('after:' + stage + ':success', stage)
  }

  void callAfterFailure(String stage) {
    nullSafeCall('after:' + stage + ':failure', stage)
  }

  void callAfterAlways(String stage) {
    nullSafeCall('after:' + stage + ':always', stage)
  }

  void callBefore(String stage) {
    nullSafeCall('before:' + stage, stage)
  }

  void callAround(String stage, Closure body) {
    def errToThrow = null
    def stageStartMillis = System.currentTimeMillis()

    callBefore(stage)
    try {
      body.call()
      callAfterSuccess(stage)
    } catch (err) {
      call('onStageFailure', stage)

      callAfterFailure(stage)
      throw err
    } finally {
      /* Deprecated, to be replaced once 'after()' is no longer in use */
      try {
        callAfter(stage)
      } catch (err) {
        call('onStageFailure', stage)
        errToThrow = err
      }
      /* end deprecated section */
      try {
        callAfterAlways(stage)
      } catch (err) {
        call('onStageFailure', stage)
        errToThrow = err
      }
      def stageDurationMillis = System.currentTimeMillis() - stageStartMillis
      nullSafeCall('after:all', stage, stageDurationMillis)
      if (errToThrow != null) {
        throw errToThrow
      }
    }
  }

  void call(String callback, String stage = null) {
    nullSafeCall(callback, stage)
  }

  private def nullSafeCall(String key, String stage) {
    nullSafeCall(key, stage, null)
  }

  private def nullSafeCall(String key, String stage, Long stageDurationMillis) {
    def body = config.bodies.get(key)
    if (body != null) {
      // Only pass the duration through to callbacks that were declared to accept it,
      // so existing single-arg 'after:all' callbacks keep working unchanged.
      if (stageDurationMillis != null && body.maximumNumberOfParameters >= 2) {
        body.call(stage, stageDurationMillis)
      } else {
        body.call(stage)
      }
    }
  }
}
