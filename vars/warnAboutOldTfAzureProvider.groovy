import uk.gov.hmcts.pipeline.deprecation.WarningCollector
import uk.gov.hmcts.pipeline.DeprecationConfig
import java.time.LocalDate

def call(String environment) {
   
  def tfDeprecationConfig = new DeprecationConfig(this).getDeprecationConfig().terraform
  writeFile file: 'warn-about-old-tf-azure-provider.sh', text: libraryResource('uk/gov/hmcts/helm/warn-about-old-tf-azure-provider.sh')

  def slackDeprecationMessage = []

  tfDeprecationConfig.each { dependency, deprecation ->
    try {
      sh """
      chmod +x warn-about-old-tf-azure-provider.sh
      ./warn-about-old-tf-azure-provider.sh $dependency $deprecation.version
      """
    } catch(ignored) {
      slackDeprecationMessage << [
          dependency: dependency,
          message: "Please update ${dependency} to the latest acceptable version ${deprecation.version}",
          deadline: deprecation.date_deadline
      ]
    } 
  }
  if (slackDeprecationMessage) {
    def formattedMessage = slackDeprecationMessage.collect { deprecation ->
      """- Dependency: ${deprecation.dependency}
        Message: ${deprecation.message}
        Deadline: ${deprecation.deadline}"""
    }.join("\n\n")

    WarningCollector.addPipelineWarning(
      "updated_terraform_versions",
      "Please update your terraform dependencies in ${environment} as per the following: \n\n${formattedMessage}",
      LocalDate.now()
    )
  }
  sh 'rm -f warn-about-old-tf-azure-provider.sh'
}
