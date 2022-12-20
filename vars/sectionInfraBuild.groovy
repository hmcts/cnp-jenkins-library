import uk.gov.hmcts.contino.MetricsPublisher
import java.time.LocalDate

def call(params) {
  def environment = params.environment
  def subscription = params.subscription
  def product = params.product
  def planOnly = params.planOnly ?: false
  def component = params.component ?: null
  def expiresAfter = params.expiresAfter ?: LocalDate.now().plusDays(30)
  def pcr = params.pipelineCallbacksRunner

  MetricsPublisher metricsPublisher = new MetricsPublisher(this, currentBuild, product, "")
  approvedEnvironmentRepository(environment, metricsPublisher) {
    withSubscription(subscription) {
      pcr.callAround("buildinfra:${environment}") {
        timeoutWithMsg(time: 150, unit: 'MINUTES', action: "buildinfra:${environment}") {
          // build environment infrastructure once
          return spinInfra(product, component, expiresAfter, environment, planOnly, subscription)
        }
      }
    }
  }
}
