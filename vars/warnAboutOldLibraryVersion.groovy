import uk.gov.hmcts.pipeline.deprecation.WarningCollector
import uk.gov.hmcts.pipeline.DeprecationConfig
import java.time.LocalDate

def call(String repoUrl = null) {

    def jenkinsLibraryDeprecationConfig = repoUrl ?
        new DeprecationConfig(this).getDeprecationConfig(repoUrl).jenkinsLibrary :
        new DeprecationConfig(this).getDeprecationConfig().jenkinsLibrary

    writeFile file: 'check-old-library-version.sh', text: libraryResource('uk/gov/hmcts/library/check-old-library-version.sh')

    jenkinsLibraryDeprecationConfig.each { configKey, deprecation ->
        try {
            sh """
            chmod +x check-old-library-version.sh
            ./check-old-library-version.sh '${deprecation.pattern}'
            """
        } catch(ignored) {
            WarningCollector.addPipelineWarning(
                "old_library_version",
                "Your code references the old library version (${deprecation.pattern}). Please update your Jenkinsfile to use the new library version: *${deprecation.version}*", 
                LocalDate.parse(deprecation.date_deadline)
            )
        }
    }
    sh 'rm -f check-old-library-version.sh'
}
