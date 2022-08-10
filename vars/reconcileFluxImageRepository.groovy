import java.time.LocalDate
import uk.gov.hmcts.contino.Environment
import uk.gov.hmcts.pipeline.deprecation.WarningCollector
import java.time.LocalDate

def call(Map<String, String> params) {

  def product = params.product
  def component = params.component

  writeFile file: 'reconcile-flux-image-repository.sh', text: libraryResource('uk/gov/hmcts/flux/reconcile-flux-image-repository.sh')

  try {
    sh """
    export AZURE_CONFIG_DIR=/opt/jenkins/.azure-jenkins
    az login --identity > /dev/null
    az aks get-credentials --resource-group $PTL_AKS_RESOURCE_GROUP --name $PTL_AKS_CLUSTER_NAME --subscription $AKS_PTL_SUBSCRIPTION_NAME -a > /dev/null 
    chmod +x reconcile-flux-image-repository.sh
    ./reconcile-flux-image-repository.sh $product $component
    """
  } finally {
    sh 'rm -f reconcile-flux-image-repository.sh'
  }

  if (fileExists('no-image-repo')) {
    WarningCollector.addPipelineWarning("image_repo_not_found", "Flux could not reconcile the image repository called $product-$component. Please check that the name of the image repository is correct.", WarningCollector.getMessageByDays.nextYear)
    sh "rm -f no-image-repo"
  }

}
