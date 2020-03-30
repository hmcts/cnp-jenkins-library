import uk.gov.hmcts.contino.Consul
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.AzPrivateDns
import uk.gov.hmcts.contino.EnvironmentDnsConfig

def call(Map params) {
  AppPipelineConfig config = params.appPipelineConfig
  boolean azPrivateFailOnError = false

  withAksClient(params.subscription, params.environment) {
    EnvironmentDnsConfig.Entry dnsConfigEntry = new EnvironmentDnsConfig(this).getEntry(params.environment)
    Consul consul = new Consul(this, params.environment)
    AzPrivateDns azPrivateDns = new AzPrivateDns(this, params.environment, environmentDnsConfigEntry)

    // Staging DNS registration
    if (config.legacyDeploymentForEnv(params.environment)) {
      withIlbIp(params.subscription, params.environment) {
        registerDns(consul, azPrivateDns, dnsConfigEntry, "${params.product}-${params.component}-${params.environment}-staging", env.TF_VAR_ilbIp)

        registerDns(consul, azPrivateDns, dnsConfigEntry, "${params.product}-${params.component}-${params.environment}", env.TF_VAR_ilbIp)
      }
    }

    aksSubscriptionName = params.aksSubscription != null ? params.aksSubscription.name : null


    if (config.aksStagingDeployment) {
      if (aksSubscriptionName) {
        def ingressIP = params.aksSubscription.ingressIp()
        registerDns(consul, azPrivateDns, dnsConfigEntry, "${params.product}-${params.component}-staging", ingressIP)
      } else {
        echo "Skipping staging dns registration for AKS as this environment is not configured with it: ${aksSubscriptionName}"
      }
    }
    // AAT + PROD DNS registration
    def aksEnv = params.aksSubscription != null && params.aksSubscription.envName

    if (aksEnv) {
      appGwIp = params.aksSubscription.loadBalancerIp()
      if (!config.legacyDeploymentForEnv(params.environment)) {
        registerDns(consul, azPrivateDns, dnsConfigEntry, "${params.product}-${params.component}-${params.environment}", appGwIp)
      }
      registerDns(consul, azPrivateDns, dnsConfigEntry, "${params.product}-${params.component}", appGwIp)
    } else {
      echo "Skipping dns registration for AKS as this environment is not configured with it: ${aksSubscriptionName}"
    }
  }
}

def registerDns(consul, azPrivateDns, dnsConfigEntry,recordName, serviceIP) {
  if (dnsConfigEntry.consulActive) {
    consul.registerDns(recordName, serviceIP)
  }
  if (dnsConfigEntry.active) {
    azPrivateDns.registerDns(recordName, serviceIP)
  }
}
