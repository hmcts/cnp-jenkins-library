import uk.gov.hmcts.contino.Consul
import uk.gov.hmcts.contino.Kubectl
import uk.gov.hmcts.contino.AppPipelineConfig

def call(Map params) {
  AppPipelineConfig config = params.appPipelineConfig

  withAksClient(params.subscription, params.environment) {
    Kubectl kubectl = new Kubectl(this, params.subscription, null, params.aksSubscription )
    kubectl.login()
    def ingressIP = kubectl.getServiceLoadbalancerIP("traefik", "admin")
    Consul consul = new Consul(this, params.environment)
    def consulApiAddr = consul.getConsulIP()

    if (!config.legacyDeployment) {
      consul.registerDns("${params.product}-${params.component}-${params.environment}", ingressIP)
    } else {
      if (params.registerAse) {
        withIlbIp(params.environment) {
          consul.registerDns("${params.product}-${params.component}-${params.environment}", env.TF_VAR_ilbIp)
        }
      }
    }
    if (config.aksStagingDeployment) {
      consul.registerDns("${params.product}-${params.component}", ingressIP)
    }
  }
}
