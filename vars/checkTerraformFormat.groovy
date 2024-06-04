import uk.gov.hmcts.pipeline.deprecation.WarningCollector

def call() {

  try {
    sh """
    chmod +x check-terraform-format.sh
    ./check-terraform-format.sh
    """
  } catch(ignored) {
    WarningCollector.addPipelineWarning("Terraform was not formatted correctly", "it has been reformatted and pushed back to your Pull Request")
  } finally {
    sh 'rm -f check-terraform-format.sh'
  }
}
