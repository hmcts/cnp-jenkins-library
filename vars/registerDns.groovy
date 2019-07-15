import uk.gov.hmcts.contino.Consul
import uk.gov.hmcts.contino.Kubectl
import uk.gov.hmcts.contino.AppPipelineConfig

def call(Map params) {
  AppPipelineConfig config = params.appPipelineConfig

  withAksClient(params.subscription, params.environment) {
    // Staging DNS registration
    if (params.isStaging) {
      Kubectl kubectl = new Kubectl(this, params.subscription, null, params.aksSubscription)
      kubectl.login()
      def ingressIP = kubectl.getServiceLoadbalancerIP("traefik", "admin")
      Consul consul = new Consul(this, params.environment)

      if (config.legacyDeployment) {
        withIlbIp(params.environment) {
          consul.registerDns("${params.product}-${params.component}-${params.environment}", env.TF_VAR_ilbIp)
        }
      }
      if (config.aksStagingDeployment) {
        consul.registerDns("${params.product}-${params.component}", ingressIP)
      }
    }
    // AAT + PROD DNS registration
    else {
      appGwIp = az "network application-gateway frontend-ip show  -g ${env.AKS_RESOURCE_GROUP} --gateway-name aks-${params.environment}-appgw --name appGatewayFrontendIP --subscription ${params.aksSubscription} --query privateIpAddress -o tsv"

      // Note: remove this when we get a PROD subscription
      if (params.aksSubscrption.contains('PROD')) {
        return
      }

      if (config.legacyDeployment) {
        withIlbIp(params.environment) {
          consul.registerDns("${params.product}-${params.component}-${params.environment}", env.TF_VAR_ilbIp)
        }
      } else {
        consul.registerDns("${params.product}-${params.component}-${params.environment}", appGwIp)
      }
      if (config.aksStagingDeployment) {
        consul.registerDns("${params.product}-${params.component}", ingressIP)
      }
    }
  }
}
