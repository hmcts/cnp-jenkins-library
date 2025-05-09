import uk.gov.hmcts.pipeline.deprecation.WarningCollector
import uk.gov.hmcts.pipeline.DeprecationConfig
import java.time.LocalDate

def call(String environment, String product, String repoUrl = null) {

    def tfDeprecationConfig = repoUrl ? 
        new DeprecationConfig(this).getDeprecationConfig(repoUrl).terraform : 
        new DeprecationConfig(this).getDeprecationConfig().terraform

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
            WarningCollector.addPipelineWarning("updated_tf_versions" ,"`${dependency}` - minimum required: *${deprecation.version}*.",  LocalDate.parse(deprecation.date_deadline))
        }
    }
    sh 'rm -f warn-about-old-tf-azure-provider.sh'
}
