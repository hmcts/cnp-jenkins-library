import uk.gov.hmcts.contino.Consul
import uk.gov.hmcts.contino.Kubectl
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.AKSSubscription

def call(Map params) {
  AppPipelineConfig config = params.appPipelineConfig

  withAksClient(params.subscription, params.environment) {
    Consul consul = new Consul(this, params.environment)

    // Staging DNS registration
    if (config.legacyDeployment) {
      withIlbIp(params.environment) {
        consul.registerDns("${params.product}-${params.component}-${params.environment}-staging", env.TF_VAR_ilbIp)
      }
    }

    // Note: update this when we get a PROD subscription
    if (config.aksStagingDeployment) {
      if (params.aksSubscription && !params.aksSubscription.contains('PROD')) {
        Kubectl kubectl = new Kubectl(this, params.subscription, null, params.aksSubscription)
        kubectl.login()
        def ingressIP = kubectl.getServiceLoadbalancerIP("traefik", "admin")
        consul.registerDns("${params.product}-${params.component}-staging", ingressIP)
      } else {
        echo "Skipping dns registration for AKS as this environment is not configured with it: ${params.aksSubscription}"
      }
    }
    // AAT + PROD DNS registration
    def az = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-${params.subscription} az $cmd", returnStdout: true).trim() }
    def aksEnv = AKSSubscription.aksEnvironment(params.environment)

    if (config.legacyDeployment) {
      withIlbIp(params.environment) {
        consul.registerDns("${params.product}-${params.component}-${params.environment}", env.TF_VAR_ilbIp)
      }
    } else if (params.aksSubscription && !params.aksSubscription.contains('PROD')) {
      // Note: update this when we get a PROD subscription
      appGwIp = az "network application-gateway frontend-ip show  -g ${params.aksInfraRg} --gateway-name aks-${aksEnv}-appgw --name appGwPrivateFrontendIp --subscription ${params.aksSubscription} --query privateIpAddress -o tsv"
      consul.registerDns("${params.product}-${params.component}-${params.environment}", appGwIp)
    } else {
      echo "Skipping dns registration for AKS as this environment is not configured with it: ${params.aksSubscription}"
    }

    // Note: update this when we get a PROD subscription
    if (config.aksStagingDeployment) {
      if (params.aksSubscription && !params.aksSubscription.contains('PROD')) {
        appGwIp = az "network application-gateway frontend-ip show  -g ${params.aksInfraRg} --gateway-name aks-${aksEnv}-appgw --name appGwPrivateFrontendIp --subscription ${params.aksSubscription} --query privateIpAddress -o tsv"
        consul.registerDns("${params.product}-${params.component}", appGwIp)
      } else {
        echo "Skipping dns registration for AKS as this environment is not configured with it: ${params.aksSubscription}"
      }
    }
  }
}
