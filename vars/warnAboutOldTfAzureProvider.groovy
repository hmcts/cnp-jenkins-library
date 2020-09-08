import uk.gov.hmcts.pipeline.deprecation.WarningCollector

def call() {

  writeFile file: 'warn-about-old-tf-azure-provider.sh', text: libraryResource('uk/gov/hmcts/helm/warn-about-old-tf-azure-provider.sh')

  try {
    sh """
    chmod +x warn-about-old-tf-azure-provider.sh
    ./warn-about-old-tf-azure-provider.sh
    """
  } catch(ignored) {
    WarningCollector.addPipelineWarning("updated_azurerm_provider", "Please upgrade azurerm to the lastest 2.x version. For an example, see: https://github.com/hmcts/draft-store/pull/702", new Date().parse("dd.MM.yyyy", "06.10.2020"))
  } finally {
    sh 'rm -f warn-about-old-tf-azure-provider.sh'
  }

}
