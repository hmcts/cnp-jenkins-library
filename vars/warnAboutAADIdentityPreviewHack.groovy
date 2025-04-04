import uk.gov.hmcts.pipeline.deprecation.WarningCollector

def call(Map<String, String> params) {
  def product = params.product
  def component = params.component

  writeFile file: 'warn-about-aad-identity-preview-hack.sh', text: libraryResource('uk/gov/hmcts/helm/warn-about-aad-identity-preview-hack.sh')

  sh """
    chmod +x warn-about-aad-identity-preview-hack.sh
    ./warn-about-aad-identity-preview-hack.sh civil citizen-ui
  """

  sh 'rm warn-about-aad-identity-preview-hack.sh'
}
