import uk.gov.hmcts.pipeline.deprecation.WarningCollector
import uk.gov.hmcts.pipeline.DeprecationConfig
import java.time.LocalDate

def call(String environment, String product) {
   
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
      WarningCollector.addPipelineWarning(
          "updated_terraform_versions",
          "`${dependency}` - minimum required is *${deprecation.version}*, update by ${deprecation.deadline}",
          LocalDate.parse(deprecation.deadline)
      )
      println("Added deprecation to WarningCollector")
    } 
  }
  sh 'rm -f warn-about-old-tf-azure-provider.sh'
}
