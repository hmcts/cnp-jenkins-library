import uk.gov.hmcts.pipeline.deprecation.WarningCollector
import uk.gov.hmcts.pipeline.DeprecationConfig
import java.time.LocalDate

def call(String environment, String product) {
  String gitUrl = env.GIT_URL
  def tfDeprecationConfig = new DeprecationConfig(this).getDeprecationConfig().terraform
  LocalDate defaultExpiryDate = null

  // Repository specific expiry dates
  switch (gitUrl.toLowerCase()) {
    case "https://github.com/hmcts/am-org-role-mapping-service.git":
        defaultExpiryDate = LocalDate.of(2024, 5, 29)
        break
  }

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
      // If no specific date was set in the switch statement, use the date from deprecation config
      def expiryDate = defaultExpiryDate ?: LocalDate.parse(deprecation.date_deadline)
      WarningCollector.addPipelineWarning("updated_tf_versions" ,"`${dependency}` - minimum required: *${deprecation.version}*.", expiryDate)
    }
  }
  sh 'rm -f warn-about-old-tf-azure-provider.sh'
}
