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
    final long stageStartedAt = System.nanoTime()
    def errToThrow = null

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

      final long elapsedMillis = (System.nanoTime() - stageStartedAt) / 1_000_000L
      final long stageDurationMillis = elapsedMillis < 0L ? 0L : elapsedMillis
      nullSafeCall('after:all', stage, stageDurationMillis)

      if (errToThrow != null) {
        throw errToThrow
      }
    }
  }

  void call(String callback, String stage = null) {
    nullSafeCall(callback, stage)
  }

  private void nullSafeCall(String key, String stage, Long stageDurationMillis = null) {
    def body = config.bodies.get(key)
    if (body != null) {
      if (stageDurationMillis != null && body.maximumNumberOfParameters > 1) {
        body.call(stage, stageDurationMillis)
      } else {
        body.call(stage)
      }
    }
  }
}
