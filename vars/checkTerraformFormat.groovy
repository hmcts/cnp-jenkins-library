import uk.gov.hmcts.pipeline.deprecation.WarningCollector

def call() {

  writeFile file: 'check-terraform-format.sh', text: libraryResource('uk/gov/hmcts/helm/check-terraform-format.sh')

  try {
    sh ""
    chmod +x check-terraform-format.sh
    ./check-terraform-format.sh
    ""
  } catch(ignored) {
    WarningCollector.addPipelineWarning("Terraform was not formatted correctly", "it has been reformatted and pushed back to your Pull Request")
  } finally {
    sh 'rm -f check-terraform-format.sh'
  }
}
