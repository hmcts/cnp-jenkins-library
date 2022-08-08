import java.time.LocalDate
import uk.gov.hmcts.contino.Environment

def call(Map<String, String> params) {

  def product = params.product
  def component = params.component
  def resourceGroup = env.PTL_AKS_RESOURCE_GROUP
  def clusterName = env.PTL_AKS_CLUSTER_NAME
  def aksSubscription = env.AKS_PTL_SUBSCRIPTION_NAME

  writeFile file: 'reconcile-flux-image-repository.sh', text: libraryResource('uk/gov/hmcts/flux/reconcile-flux-image-repository.sh')

  try {
    sh """
    chmod +x reconcile-flux-image-repository.sh
    ./reconcile-flux-image-repository.sh $product $component $resourceGroup $clusterName $aksSubscription
    """
  } finally {
    sh 'rm -f reconcile-flux-image-repository.sh'
  }

}
