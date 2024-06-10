import uk.gov.hmcts.pipeline.deprecation.WarningCollector
import uk.gov.hmcts.pipeline.DeprecationConfig
import java.time.LocalDate
import groovy.json.JsonOutput

def call() {
   
  def tfDeprecationConfig = new DeprecationConfig(this).getDeprecationConfig().terraform
  def slackDeprecationMessage = []
  def processedDependencies = [:]
  writeFile file: 'warn-about-old-tf-azure-provider.sh', text: libraryResource('uk/gov/hmcts/helm/warn-about-old-tf-azure-provider.sh')

  tfDeprecationConfig.each { dependency, deprecation ->
    try {
      sh """
      chmod +x warn-about-old-tf-azure-provider.sh
      ./warn-about-old-tf-azure-provider.sh $dependency $deprecation.version
      """
    } catch(ignored) {
      // Only add to slack message new messages
      if !(processedDependencies.containsKey(dependency)) {
        slackDeprecationMessage << [
          dependency: dependency,
          message: "Please update your terraform ${dependency} to the latest acceptable version ${deprecation.version}",
          deadline: deprecation.date_deadline
        ]
        processedDependencies[dependency] = true
      }
    } 
  }
  sh 'rm -f warn-about-old-tf-azure-provider.sh'

  if (slackDeprecationMessage) {
    def jsonMessage = JsonOutput.toJson(slackDeprecationMessage)
    WarningCollector.addPipelineWarning(
      "updated_terraform_versions",
      "Please update your terraform dependencies as per the following details: ${jsonMessage}"
    )
  }
}
