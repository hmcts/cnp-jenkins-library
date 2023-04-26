import uk.gov.hmcts.pipeline.deprecation.WarningCollector

def call(Map<String, String> params) {
  def product = params.product
  def component = params.component

  writeFile file: 'warn-about-workload-identity.sh', text: libraryResource('uk/gov/hmcts/helm/warn-about-workload-identity.sh')
  try {
    sh """
      chmod +x warn-about-workload-identity.sh
      ./warn-about-workload-identity.sh $product $component
    """
  } catch(ignored) {
    WarningCollector.addPipelineWarning("workoad_identity_set", "Please migrate your application to use Workload Identity, as aad-pod-identity project is deprecated.", new Date().parse("dd.MM.yyyy", "30.06.2023"))
  }

  sh 'rm warn-about-workload-identity.sh'
}
