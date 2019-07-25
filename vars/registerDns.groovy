import uk.gov.hmcts.contino.Consul
import uk.gov.hmcts.contino.AppPipelineConfig

def call(Map params) {
  AppPipelineConfig config = params.appPipelineConfig

  withAksClient(params.subscription, params.environment) {
    Consul consul = new Consul(this, params.environment)

    // Staging DNS registration
    if (config.legacyDeployment) {
      withIlbIp(params.environment) {
        consul.registerDns("${params.product}-${params.component}-${params.environment}-staging", env.TF_VAR_ilbIp)
        consul.registerDns("${params.product}-${params.component}-${params.environment}", env.TF_VAR_ilbIp)
      }
    }

    aksSubscriptionName = params.aksSubscription != null ? params.aksSubscription.name : null

    // Note: update this when we get a PROD subscription
    if (config.aksStagingDeployment) {
      if (aksSubscriptionName && !aksSubscriptionName.contains('PROD')) {
        def ingressIP = params.aksSubscription.ingressIp()
        consul.registerDns("${params.product}-${params.component}-staging", ingressIP)
      } else {
        echo "Skipping dns registration for AKS as this environment is not configured with it: ${aksSubscriptionName}"
      }
    }
    // AAT + PROD DNS registration
    def aksEnv = params.aksSubscription != null && params.aksSubscription.envName

    if (!config.legacyDeployment && aksEnv && !aksSubscriptionName.contains('PROD')) {
      // Note: update this when we get a PROD subscription
      appGwIp = params.aksSubscription.loadBalancerIp()
      consul.registerDns("${params.product}-${params.component}-${params.environment}", appGwIp)
      consul.registerDns("${params.product}-${params.component}", appGwIp)
    } else {
      echo "Skipping dns registration for AKS as this environment is not configured with it: ${aksSubscriptionName}"
    }
  }
}
