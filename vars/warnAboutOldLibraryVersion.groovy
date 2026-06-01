import uk.gov.hmcts.pipeline.deprecation.WarningCollector
import uk.gov.hmcts.pipeline.DeprecationConfig
import java.time.LocalDate

def call(String repoUrl = null) {

    def acrDeprecationConfig = repoUrl ?
        new DeprecationConfig(this).getDeprecationConfig(repoUrl).acr :
        new DeprecationConfig(this).getDeprecationConfig().acr

    writeFile file: 'check-old-acr-references.sh', text: libraryResource('uk/gov/hmcts/acr/check-old-acr-references.sh')

    acrDeprecationConfig.each { configKey, deprecation ->
        try {
            sh """
            chmod +x check-old-acr-references.sh
            ./check-old-acr-references.sh '${deprecation.pattern}'
            """
        } catch(ignored) {
            WarningCollector.addPipelineWarning(
                "old_acr_registry",
                "Your code references the old Azure Container Registry domains (${deprecation.pattern}). Please update Dockerfiles, Helm charts and templates to use the new registries: *${deprecation.new_registries}*",
                LocalDate.parse(deprecation.date_deadline)
            )
        }
    }
    sh 'rm -f check-old-acr-references.sh'
}
