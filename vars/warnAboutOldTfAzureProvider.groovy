import uk.gov.hmcts.pipeline.deprecation.WarningCollector

import java.time.LocalDate

def call() {

  writeFile file: 'warn-about-old-tf-azure-provider.sh', text: libraryResource('uk/gov/hmcts/helm/warn-about-old-tf-azure-provider.sh')

  try {
    sh """
    chmod +x warn-about-old-tf-azure-provider.sh
    ./warn-about-old-tf-azure-provider.sh
    """
  } catch(ignored) {
    WarningCollector.addPipelineWarning("updated_azurerm_provider", "Please upgrade azurerm to the latest 3.x version, and terraform to 1.x For examples see: https://github.com/hmcts/spring-boot-template/pull/412 and https://github.com/hmcts/draft-store/pull/1168", LocalDate.of(2023, 02, 28))
  } finally {
    sh 'rm -f warn-about-old-tf-azure-provider.sh'
  }
}
