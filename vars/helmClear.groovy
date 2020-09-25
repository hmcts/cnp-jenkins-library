import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.Kubectl
import uk.gov.hmcts.contino.Helm

def call(DockerImage dockerImage, Map params) {
  
  def subscription = params.subscription
  def environment = params.environment
  def product = params.product
  def component = params.component
  def aksServiceName = dockerImage.getAksServiceName()

  def chartName = "${product}-${component}"

  def kubectl = new Kubectl(this, subscription, aksServiceName, params.aksSubscription.name)
  kubectl.login()

  def namespace = env.TEAM_NAMESPACE

  // For use in deleting deployments that are no longer needed
  def deleted = false
  if (helm.exists(dockerImage.getImageTag(), namespace) &&
    !helm.hasAnyDeployed(dockerImage.getImageTag(), namespace)) {

    deleted = true
    helm.delete(dockerImage.getImageTag(), namespace)
    echo "Deleted release for ${dockerImage.getImageTag()}"
  } else {
    echo "Skipping delete for ${dockerImage.getImageTag()} as it doesn't exist"
  }

}
