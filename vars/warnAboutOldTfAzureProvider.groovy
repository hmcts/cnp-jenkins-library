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
    LocalDate deadlineDate = gitUrl?.toLowerCase() == "https://github.com/hmcts/cnp-plum-shared-infrastructure.git" 
      ? LocalDate.of(2024, 7, 2)
      : LocalDate.parse(deprecation.date_deadline)
    
    if (dependency == "registry.terraform.io/hashicorp/azurerm" && LocalDate.now().isAfter(deadlineDate)) {
        error "Dependency ${dependency} has expired on ${deadlineDate}. Please upgrade to version ${deprecation.version} or higher."
    }
    
    try {
      sh """
      echo "GitURL is $gitUrl"
      chmod +x warn-about-old-tf-azure-provider.sh
      ./warn-about-old-tf-azure-provider.sh $dependency $deprecation.version
      """
    } catch(ignored) {
      WarningCollector.addPipelineWarning("updated_tf_versions" ,"`${dependency}` - minimum required: *${deprecation.version}*.", deadlineDate)
    }
  }
  sh 'rm -f warn-about-old-tf-azure-provider.sh'
}