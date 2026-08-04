import uk.gov.hmcts.contino.Kubectl
import uk.gov.hmcts.contino.Helm
import uk.gov.hmcts.pipeline.AgentSelector

def call(dockerImage, Map params, pcr) {
  try {
    stage("Uninstall Helm Release - ${params.environment}") {
      def uninstallBody = {
        pcr.callAround("helmReleaseUninstall:${params.environment}") {
          withAksClient(params.subscription, params.environment, params.product) {
            uninstallRelease(dockerImage, params)
          }
        }
      }

      if (AgentSelector.isRunningOnEnvironmentAgent(env, params.environment, params.product)) {
        withDockerAgent(params.product, uninstallBody)
      } else {
        withEnvironmentAgent(params.environment, params.product, true) {
          withDockerAgent(params.product, uninstallBody)
        }
      }
    }
  } catch (ignored) {
    echo "Unable to uninstall this helm release."
  }
}

def uninstallRelease(dockerImage, Map params) {

  def subscription = params.subscription

  def aksServiceName = dockerImage.getAksServiceName()

  def namespace = env.TEAM_NAMESPACE

  def chartName = "${params.product}-${params.component}"

  def kubectl = new Kubectl(this, subscription, aksServiceName, params.aksSubscription.name)
  kubectl.login()

  def helm = new Helm(this, chartName)

  try {
    if (helm.exists(dockerImage.getImageTag(), namespace)) {
      helm.delete(dockerImage.getImageTag(), namespace)
      echo "Uninstalled release for ${dockerImage.getImageTag()}"
    }
    def releaseName = "${chartName}-${dockerImage.getImageTag()}"
    kubectl.deleteJobsByReleasePrefix(namespace, releaseName)
  } catch (ignored) {
      echo "Unable to uninstall this helm release."
  }

}
