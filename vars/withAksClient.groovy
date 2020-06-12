import uk.gov.hmcts.contino.Environment

def call(String subscription, String environment, Closure block) {
  withAcrClient(subscription) {
    def envName = environment.toUpperCase()
    env.AKS_CLUSTER_NAME = env."${envName}_AKS_CLUSTER_NAME" ?: "cnp-${environment}-cluster"
    env.AKS_RESOURCE_GROUP = env."${envName}_AKS_RESOURCE_GROUP" ?: "cnp-${environment}-rg"
    block.call()
  }
}

def call(String subscription, Closure block) {
  String environment = new Environment(env).previewName
  call(subscription,environment, block)
}

