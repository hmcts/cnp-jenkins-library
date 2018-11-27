package uk.gov.hmcts.contino

class PipelineCallbacks implements Serializable {

  Map<String, Closure> bodies = new HashMap<>()
  String slackChannel
  List<Map<String, Object>> vaultSecrets = []
  String vaultName
  boolean migrateDb = false
  private MetricsPublisher metricsPublisher
  String[] charts

  boolean performanceTest = false
  boolean apiGatewayTest = false
  boolean crossBrowserTest = false
  boolean mutationTest = false
  boolean dockerBuild = false
  boolean deployToAKS = false
  boolean installCharts = false

  int crossBrowserTestTimeout
  int perfTestTimeout
  int apiGatewayTestTimeout
  int mutationTestTimeout
  int fullFunctionalTestTimeout
  boolean fullFunctionalTest = false

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

  void setVaultName(String vaultName) {
    this.vaultName  = vaultName
  }

  void enableDbMigration() {
    this.migrateDb = true
  }

  void enablePerformanceTest(int timeout = 15) {
    this.perfTestTimeout = timeout
    this.performanceTest = true
  }

  void enableApiGatewayTest(int timeout = 15) {
    this.apiGatewayTestTimeout = timeout
    this.apiGatewayTest = true
  }

  void enableCrossBrowserTest(int timeout = 120) {
    this.crossBrowserTestTimeout = timeout
    this.crossBrowserTest = true
  }

  void enableDockerBuild() {
    this.dockerBuild = true
  }

  void enableDeployToAKS() {
    this.deployToAKS = true
    this.installCharts = false
  }

  void installCharts(List<String> charts) {
    this.installCharts = true
    this.deployToAKS = false
    this.charts = charts as String[]
  }

  void installCharts() {
    this.installCharts = true
    this.deployToAKS = false
    this.charts = [] as String[]
  }

  void enableFullFunctionalTest(int timeout = 30) {
    this.fullFunctionalTestTimeout = timeout
    this.fullFunctionalTest = true
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
