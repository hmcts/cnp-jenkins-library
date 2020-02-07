import uk.gov.hmcts.pipeline.deprecation.WarningCollector

def call(Map<String, String> params) {
  def product = params.product
  def component = params.component

  writeFile file: 'warn-about-aad-identity-preview-hack.sh', text: libraryResource('uk/gov/hmcts/helm/warn-about-aad-identity-preview-hack.sh')

  try {
    sh """
    chmod +x warn-about-aad-identity-preview-hack.sh
    ./warn-about-aad-identity-preview-hack.sh $product $component
  """
  } catch(ignored) {
    WarningCollector.addPipelineWarning("aadidentityname_set_for_preview", "Please remove aadidentityname from your values.preview.template.yaml it is no longer needed there and will not work on the new Jenkins that is being rolled out.", new Date().parse("dd.MM.yyyy", "28.02.2020"))
  }

  sh 'rm warn-about-aad-identity-preview-hack.sh'
}
