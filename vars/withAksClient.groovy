import uk.gov.hmcts.contino.Environment

def call(String subscription, String environment, String product, Closure block) {
  withAcrClient(subscription) {
    // replacing idam is a hack to workaround incorrect idam environment value
    def envName = environment.replace('idam-', '').toUpperCase()
    env.AKS_CLUSTER_NAME = env."${envName}_AKS_CLUSTER_NAME" ?: "cnp-${environment}-cluster"
    env.AKS_RESOURCE_GROUP = env."${envName}_AKS_RESOURCE_GROUP" ?: "cnp-${environment}-rg"
    block.call()
  }
}

def call(String subscription, String product, Closure block) {
  String environment = new Environment(env).previewName
  call(subscription, environment, product, block)
}
