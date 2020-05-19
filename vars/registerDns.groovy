import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.AzPrivateDns
import uk.gov.hmcts.contino.EnvironmentDnsConfig
import uk.gov.hmcts.contino.EnvironmentDnsConfigEntry

def call(Map params) {
  AppPipelineConfig config = params.appPipelineConfig

  withAksClient(params.subscription, params.environment) {
    EnvironmentDnsConfigEntry dnsConfigEntry = new EnvironmentDnsConfig(this).getEntry(params.environment)
    AzPrivateDns azPrivateDns = new AzPrivateDns(this, params.environment, dnsConfigEntry)

    aksSubscriptionName = params.aksSubscription != null ? params.aksSubscription.name : null

    // AAT + PROD DNS registration
    def aksEnv = params.aksSubscription != null && params.aksSubscription.envName

    if (aksEnv) {
      appGwIp = params.aksSubscription.loadBalancerIp()
      if (!config.legacyDeploymentForEnv(params.environment)) {
        azPrivateDns.registerDns("${params.product}-${params.component}-${params.environment}", appGwIp)
      }
      azPrivateDns.registerDns("${params.product}-${params.component}", appGwIp)
    } else {
      echo "Skipping dns registration for AKS as this environment is not configured with it: ${aksSubscriptionName}"
    }
  }
}

