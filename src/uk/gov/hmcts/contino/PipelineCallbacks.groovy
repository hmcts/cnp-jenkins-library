package uk.gov.hmcts.contino

class PipelineCallbacks implements Serializable {

  Map<String, Closure> bodies = new HashMap<>()
  String slackChannel
  List<Map<String, Object>> vaultSecrets = []
  boolean migrateDb = false
  private MetricsPublisher metricsPublisher

  boolean performanceTest = false
  boolean crossBrowserTest = false
  boolean mutationTest = false
  int crossBrowserTestTimeout
  int perfTestTimeout
  int mutationTestTimeout

  PipelineCallbacks(MetricsPublisher metricsPublisher) {
    this.metricsPublisher = metricsPublisher
  }

  void afterCheckout(Closure body) {
    after('checkout', body)
  }

  void before(String stage, Closure body) {
    bodies.put('before:' + stage, body)
  }

  void after(String stage, Closure body) {
    bodies.put('after:' + stage, body)
  }

  void callAfter(String stage) {
    nullSafeCall('after:' + stage)
    metricsPublisher.publish(stage)
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

  void onStageFailure(Closure body) {
    bodies.put('onStageFailure', body)
  }

  void onFailure(Closure body) {
    bodies.put('onFailure', body)
  }

  void onSuccess(Closure body) {
    bodies.put('onSuccess', body)
  }

  void enableSlackNotifications(String slackChannel) {
    this.slackChannel = slackChannel
  }

  void loadVaultSecrets(List<Map<String, Object>> vaultSecrets) {
    this.vaultSecrets = vaultSecrets
  }

  void enableDbMigration() {
    this.migrateDb = true
  }

  void enablePerformanceTest(int timeout = 15) {
    this.perfTestTimeout = timeout
    this.performanceTest = true
  }

  void enableCrossBrowserTest(int timeout = 120) {
    this.crossBrowserTestTimeout = timeout
    this.crossBrowserTest = true
  }

  void enableMutationTest(int timeout = 120) {
    this.mutationTestTimeout = timeout
    this.mutationTest = true
  }

  private def nullSafeCall(String key) {
    def body = bodies.get(key)
    if (body != null) {
      body.call()
    }
  }
}
