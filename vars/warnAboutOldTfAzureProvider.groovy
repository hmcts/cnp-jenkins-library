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
    WarningCollector.addPipelineWarning("updated_azurerm_provider", "Please upgrade azurerm to the lastest 2.x version. For examples see: https://github.com/hmcts/rpe-pdf-service/pull/281 and https://github.com/hmcts/rpe-shared-infrastructure/pull/11", LocalDate.of(2020, 10, 13))
  } finally {
    sh 'rm -f warn-about-old-tf-azure-provider.sh'
  }

}
