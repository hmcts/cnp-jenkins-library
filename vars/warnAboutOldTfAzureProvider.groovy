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
      slackDeprecationMessage << [
          dependency: dependency,
          message: "minimum required is *${deprecation.version}*",
          deadline: deprecation.date_deadline
      ]
      deprecationDeadlines << deprecation.date_deadline
    } 
  }
  if (slackDeprecationMessage) {
    def formattedMessage = slackDeprecationMessage.collect { deprecation ->
      "`${deprecation.dependency}` - ${deprecation.message}, update by ${deprecation.deadline}"
    }.join("\n\n")

    def earliestDeadline = deprecationDeadlines.min()
    WarningCollector.addPipelineWarning(
      "updated_terraform_versions",
      "\n\nOutdated terraform configuration in ${environment} for ${product}: \n\n${formattedMessage}\n\n",
      LocalDate.parse(earliestDeadline)
    )
  }
  sh 'rm -f warn-about-old-tf-azure-provider.sh'
}
