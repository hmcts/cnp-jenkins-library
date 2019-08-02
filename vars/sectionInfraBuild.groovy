
def call(params) {
  def pipelineConfig = params.pipelineConfig
  def environment = params.environment
  def subscription = params.subscription
  def product = params.product
  def planOnly = params.planOnly ?: false
  def deploymentTargets = params.deploymentTargets ?: deploymentTargets(subscription, environment)

  approvedEnvironmentRepository(environment) {
    withSubscription(subscription) {
      withIlbIp(environment) {
        // build environment infrastructure once
        tfOutput = spinInfra(product, null, environment, planOnly, subscription)

        // build deployment target infrastructure for each deployment target
        folderExists('deploymentTarget') {
          dir('deploymentTarget') {
            for (int i = 0; i < deploymentTargets.size(); i++) {
              spinInfra(product, null, environment, planOnly, subscription, deploymentTargets[i])
            }
          }
        }
      }
    }
  }
}
