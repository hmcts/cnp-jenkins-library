import uk.gov.hmcts.contino.AzPrivateDns
import uk.gov.hmcts.contino.EnvironmentDnsConfig
import uk.gov.hmcts.contino.EnvironmentDnsConfigEntry

def call(Map params) {
  withAksClient(params.subscription, params.environment, params.product) {
    EnvironmentDnsConfigEntry dnsConfigEntry = new EnvironmentDnsConfig(this).getEntry(params.environment)
    AzPrivateDns azPrivateDns = new AzPrivateDns(this, params.environment, dnsConfigEntry)

    aksSubscriptionName = params.aksSubscription != null ? params.aksSubscription.name : null

    // AAT + PROD DNS registration
    def aksEnv = params.aksSubscription != null && params.aksSubscription.envName

    if (aksEnv) {
      appGwIp = params.aksSubscription.loadBalancerIp()
      azPrivateDns.registerDns("${params.product}-${params.component}-${params.environment}", appGwIp)
    } else {
      error "Could not register dns as this environment is not configured with it: ${aksSubscriptionName}"
    }
  }
}

