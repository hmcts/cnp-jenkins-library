import uk.gov.hmcts.contino.Environment

def call(String subscription, String environment, String product, Closure block) {
  withAcrClient(subscription) {
    // replacing idam is a hack to workaround incorrect idam environment value
    def envName = environment.replace('idam-', '').toUpperCase()
    env.AKS_CLUSTER_NAME = cft-preview-01-aks
    env.AKS_RESOURCE_GROUP = cft-preview-01-rg
    block.call()
  }
}

def call(String subscription, String product, Closure block) {
  String environment = new Environment(env).previewName
  call(subscription, environment, product, block)
}
