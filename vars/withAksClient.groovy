import uk.gov.hmcts.contino.Environment

def call(String subscription, String environment, Closure block) {
  withDocker('hmcts/cnp-aks-client:az-2.0.61-kubectl-1.16.2-helm-3.0.2', null) {
    withSubscription(subscription) {
      withRegistrySecrets {
        def envName = environment.replace('idam-', '').toUpperCase()
        env.AKS_CLUSTER_NAME = env."${envName}_AKS_CLUSTER_NAME" ?: "cnp-${environment}-cluster"
        env.AKS_RESOURCE_GROUP = env."${envName}_AKS_RESOURCE_GROUP" ?: "cnp-${environment}-rg"
        block.call()
      }
    }
  }
}


def call(String subscription, Closure block) {
  String environment = new Environment(env).previewName
  call(subscription,environment, block)
}

