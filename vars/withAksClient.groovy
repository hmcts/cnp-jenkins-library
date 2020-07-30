def call(String subscription, String environment, String product, Closure block) {
  withAcrClient(subscription, product) {
    def envName = environment.toUpperCase()
    env.AKS_CLUSTER_NAME = env."${envName}_AKS_CLUSTER_NAME" ?: "cnp-${environment}-cluster"
    env.AKS_RESOURCE_GROUP = env."${envName}_AKS_RESOURCE_GROUP" ?: "cnp-${environment}-rg"
    block.call()
  }
}

