import uk.gov.hmcts.contino.Kubectl
import uk.gov.hmcts.contino.Helm

def call(dockerImage, Map params, pcr) {
  try {
    stageWithAgent("Uninstall Helm Release - ${params.environment}", params.product) {
      pcr.callAround("helmReleaseUninstall:${params.environment}") {
        withAksClient(params.subscription, params.environment, params.product) {
          uninstallRelease(dockerImage, params)
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
  } catch (ignored) {
      echo "Unable to uninstall this helm release."
  }

}
