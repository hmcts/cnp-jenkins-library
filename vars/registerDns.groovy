package uk.gov.hmcts.contino

import uk.gov.hmcts.contino.Consul
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.AzPrivateDns

def call(Map params) {
  AppPipelineConfig config = params.appPipelineConfig

  withAksClient(params.subscription, params.environment) {
    Consul consul = new Consul(this, params.environment)
    AzPrivateDns azPrivateDns = new AzPrivateDns(this)

    // Staging DNS registration
    if (config.legacyDeploymentForEnv(params.environment)) {
      withIlbIp(params.subscription, params.environment) {
        consul.registerDns("${params.product}-${params.component}-${params.environment}-staging", env.TF_VAR_ilbIp)
        consul.registerDns("${params.product}-${params.component}-${params.environment}", env.TF_VAR_ilbIp)

        azPrivateDns.registerAzDns("${params.product}-${params.component}-${params.environment}-staging", env.TF_VAR_ilbIp)
        azPrivateDns.registerAzDns("${params.product}-${params.component}-${params.environment}", env.TF_VAR_ilbIp)

      }
    }

    aksSubscriptionName = params.aksSubscription != null ? params.aksSubscription.name : null


    if (config.aksStagingDeployment) {
      if (aksSubscriptionName) {
        def ingressIP = params.aksSubscription.ingressIp()
        consul.registerDns("${params.product}-${params.component}-staging", ingressIP)
      } else {
        echo "Skipping staging dns registration for AKS as this environment is not configured with it: ${aksSubscriptionName}"
      }
    }
    // AAT + PROD DNS registration
    def aksEnv = params.aksSubscription != null && params.aksSubscription.envName

    if (aksEnv) {
      appGwIp = params.aksSubscription.loadBalancerIp()
      if (!config.legacyDeploymentForEnv(params.environment)) {
        consul.registerDns("${params.product}-${params.component}-${params.environment}", appGwIp)
      }
      consul.registerDns("${params.product}-${params.component}", appGwIp)
    } else {
      echo "Skipping dns registration for AKS as this environment is not configured with it: ${aksSubscriptionName}"
    }
  }
}
