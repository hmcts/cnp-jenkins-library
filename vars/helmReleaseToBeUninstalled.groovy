/**
 * helmReleaseToBeUninstalled(environment, subscription, product)
 * 
 */

def call(DockerImage dockerImage, Map params) {

  try {
    stageWithAgent("Uninstall Helm Release ${params.environment}", params.product) {
      pcr.callAround("helmReleaseUninstall:${params.environment}") {
        withAksClient(params.subscription, params.environment, params.product) {
          helmUninstall(dockerImage, params)
        }
      }
    }
  } catch (ignored) {
    echo "Unable to uninstall this helm release."
  }
}
  