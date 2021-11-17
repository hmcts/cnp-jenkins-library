import uk.gov.hmcts.contino.MetricsPublisher

def call(params) {
  def environment = params.environment
  def subscription = params.subscription
  def product = params.product
  def planOnly = params.planOnly ?: false
  def component = params.component ?: null

  MetricsPublisher metricsPublisher = new MetricsPublisher(this, currentBuild, product, "", subscription )
  approvedEnvironmentRepository(environment, metricsPublisher) {
    withSubscription(subscription) {
      // build environment infrastructure once
      tfOutput = spinInfra(product, component, environment, planOnly, subscription)
    }
  }
}
