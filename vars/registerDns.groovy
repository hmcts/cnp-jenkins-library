import uk.gov.hmcts.contino.Consul
import uk.gov.hmcts.contino.Kubectl
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.AKSSubscription

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
      def az = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-${params.subscription} az $cmd", returnStdout: true).trim() }
      def aksEnv = AKSSubscription.aksEnvironment(params.environment)
      appGwIp = az "network application-gateway frontend-ip show  -g ${params.aksInfraRg} --gateway-name aks-${aksEnv}-appgw --name appGatewayFrontendIP --subscription ${params.aksSubscription} --query privateIpAddress -o tsv"

      // Note: remove this when we get a PROD subscription
      if (params.aksSubscription.contains('PROD')) {
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
