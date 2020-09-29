import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.Kubectl
import uk.gov.hmcts.contino.Helm

def call(DockerImage dockerImage, Map params) {
  
  def subscription = params.subscription
  def product = params.product
  def component = params.component

  def aksServiceName = dockerImage.getAksServiceName()

  def namespace = env.TEAM_NAMESPACE

  def chartName = "${product}-${component}"

  def kubectl = new Kubectl(this, subscription, aksServiceName, params.aksSubscription.name)
  kubectl.login()

  def helm = new Helm(this, chartName)

  if (helm.exists(dockerImage.getImageTag(), namespace)) {
    helm.delete(dockerImage.getImageTag(), namespace)
    echo "Uninstalled release for ${dockerImage.getImageTag()}"
  }

}
