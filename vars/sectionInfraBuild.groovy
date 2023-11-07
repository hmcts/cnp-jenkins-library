import uk.gov.hmcts.contino.MetricsPublisher
import java.time.LocalDate

def call(params) {
  def environment = params.environment
  def subscription = params.subscription
  def product = params.product
  def planOnly = params.planOnly ?: false
  def component = params.component ?: null
  def expires = params.expires ?: LocalDate.now().plusDays(30)
  def pcr = params.pipelineCallbacksRunner
  def businessArea = env.BUSINESS_AREA_TAG

  MetricsPublisher metricsPublisher = new MetricsPublisher(this, currentBuild, product, "")
  approvedEnvironmentRepository(environment, metricsPublisher) {
    withSubscription(subscription) {
      pcr.callAround("buildinfra:${environment}") {
        timeoutWithMsg(time: 150, unit: 'MINUTES', action: "buildinfra:${environment}") {
          // withAksClient adds the cluster name and RG to env vars  -- only used in CFT Sandbox 
           if ( environment == "sandbox" && params.autoStartSubscription && businessArea == "CFT" ){
            withAksClient(subscription, environment, product) {
              startEnvironmentIfRequired params
            }
           }

          // build environment infrastructure once
           return spinInfra(
              product: product,
              component: component,
              expires: expires,
              environment: environment,
              tfPlanOnly: planOnly,
              subscription: subscription
             )
        }
      }
    }
  }
}
