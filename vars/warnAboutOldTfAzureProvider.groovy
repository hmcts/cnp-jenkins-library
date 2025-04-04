import uk.gov.hmcts.pipeline.deprecation.WarningCollector
import uk.gov.hmcts.pipeline.DeprecationConfig
import java.time.LocalDate

def call(String environment, String product) {
  String gitUrl = env.GIT_URL

  def tfDeprecationConfig = new DeprecationConfig(this).getDeprecationConfig().terraform
  writeFile file: 'warn-about-old-tf-azure-provider.sh', text: libraryResource('uk/gov/hmcts/helm/warn-about-old-tf-azure-provider.sh')

  def slackDeprecationMessage = []
  def deprecationDeadlines = []

  tfDeprecationConfig.each { dependency, deprecation ->
    try {
      sh """
      chmod +x warn-about-old-tf-azure-provider.sh
      ./warn-about-old-tf-azure-provider.sh $dependency $deprecation.version
      """
    } catch(ignored) {
      LocalDate deadlineDate

      if (dependency == "registry.terraform.io/hashicorp/azurerm") {
        switch (gitUrl?.toLowerCase()) {
          case "https://github.com/hmcts/hmc-shared-infrastructure.git":
          case "https://github.com/hmcts/hmc-operational-reports-runner.git":
          case "https://github.com/hmcts/hmc-hmi-inbound-adapter.git":
          case "https://github.com/hmcts/hmc-hmi-outbound-adapter.git":
          case "https://github.com/hmcts/hmc-cft-hearing-service.git":
            deadlineDate = LocalDate.of(2024, 4, 25)
            break
          default:
            deadlineDate = LocalDate.parse(deprecation.date_deadline)
        }
      } else {
        deadlineDate = LocalDate.parse(deprecation.date_deadline)
      }

      WarningCollector.addPipelineWarning("updated_tf_versions" ,"`${dependency}` - minimum required: *${deprecation.version}*.", deadlineDate)
    }
  }
  sh 'rm -f warn-about-old-tf-azure-provider.sh'
}
