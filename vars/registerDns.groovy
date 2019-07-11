import uk.gov.hmcts.contino.Consul
import uk.gov.hmcts.contino.Kubectl
import uk.gov.hmcts.contino.AppPipelineConfig

def call(Map params) {
  AppPipelineConfig config = params.appPipelineConfig

  withAksClient(params.subscriptionName, params.environmentName) {
    Kubectl kubectl = new Kubectl(this, params.subscriptionName, null, params.aksSubscription )
    kubectl.login()
    def ingressIP = kubectl.getServiceLoadbalancerIP("traefik", "admin")
    Consul consul = new Consul(this, params.environmentName)
    def consulApiAddr = consul.getConsulIP()

    if (!config.legacyDeployment) {
      consul.registerDns("${params.product}-${params.component}-${params.environmentName}", ingressIP)
    } else {
      if (params.registerAse) {
        withIlbIp(params.environmentName) {
          consul.registerDns("${params.product}-${params.component}-${params.environmentName}", env.TF_VAR_ilbIp)
        }
      }
    }
    if (config.aksStagingDeployment) {
      consul.registerDns("${params.product}-${params.component}", ingressIP)
    }
  }
}
