import uk.gov.hmcts.pipeline.deprecation.WarningCollector
import uk.gov.hmcts.pipeline.DeprecationConfig
import java.time.LocalDate

def call() {
   
  def tfDeprecationConfig = new DeprecationConfig(this).getDeprecationConfig().terraform

  writeFile file: 'warn-about-old-tf-azure-provider.sh', text: libraryResource('uk/gov/hmcts/helm/warn-about-old-tf-azure-provider.sh')


  tfDeprecationConfig.each { dependency, deprecation ->
    try {
      sh """
      chmod +x warn-about-old-tf-azure-provider.sh
      ./warn-about-old-tf-azure-provider.sh $dependency $deprecation.version $deprecation
      """

      // maybe script needs output here to make a useful slack message?
    } catch(ignored) {
      WarningCollector.addPipelineWarning("updated_terraform_versions", "Please do some T.B.D.", LocalDate.parse(deprecation.date_deadline))
    } 
  }
  sh 'rm -f warn-about-old-tf-azure-provider.sh'
}
