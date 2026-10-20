package uk.gov.hmcts.contino

import uk.gov.hmcts.pipeline.deprecation.WarningCollector

import java.time.LocalDate

class PipelineCallbacksRunner implements Serializable {
  def final config
  private final MetricsPublisher metricsPublisher

  PipelineCallbacksRunner(config, MetricsPublisher metricsPublisher = null) {
    this.config = config
    this.metricsPublisher = metricsPublisher
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
    Throwable failure = null
    String executionId = UUID.randomUUID().toString()
    metricsPublisher?.publishStep(stage, 'STARTED', executionId)

    try {
      callBefore(stage)
      body.call()
      callAfterSuccess(stage)
    } catch (err) {
      failure = err
      call('onStageFailure', stage)

      callAfterFailure(stage)
      throw err
    } finally {
      /* Deprecated, to be replaced once 'after()' is no longer in use */
      try {
        callAfter(stage)
      } catch (err) {
        failure = err
        call('onStageFailure', stage)
        errToThrow = err
      }
      /* end deprecated section */
      try {
        callAfterAlways(stage)
      } catch (err) {
        failure = err
        call('onStageFailure', stage)
        errToThrow = err
      }
      try {
        nullSafeCall('after:all', stage)
      } catch (err) {
        failure = err
        throw err
      } finally {
        metricsPublisher?.publishStep(stage, failure == null ? 'SUCCESS' : 'FAILURE', executionId, failure)
      }
      if (errToThrow != null) {
        throw errToThrow
      }
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
