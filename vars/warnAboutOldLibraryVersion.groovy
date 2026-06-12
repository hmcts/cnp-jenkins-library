import uk.gov.hmcts.pipeline.deprecation.WarningCollector
import uk.gov.hmcts.pipeline.DeprecationConfig
import java.time.LocalDate

def call(String repoUrl = null) {

    def jenkinsLibraryDeprecationConfig = repoUrl ?
        new DeprecationConfig(this).getDeprecationConfig(repoUrl).jenkins :
        new DeprecationConfig(this).getDeprecationConfig().jenkins

    writeFile file: 'check-old-library-version.sh', text: libraryResource('uk/gov/hmcts/library/check-old-library-version.sh')

    jenkinsLibraryDeprecationConfig.each { configKey, deprecation ->
        def patterns = []
        if (deprecation.pattern instanceof Collection) {
            patterns.addAll(deprecation.pattern)
        } else {
            patterns.addAll(
                deprecation.pattern
                    .toString()
                    .split(/\|/)
                    .collect { it.trim() }
                    .findAll { it }
            )
        }

        patterns.each { pattern ->
            try {
                sh """
                chmod +x check-old-library-version.sh
                ./check-old-library-version.sh '${pattern}' '${deprecation.version}' '${deprecation.date_deadline}'
                """
            } catch(ignored) {
                WarningCollector.addPipelineWarning(
                    "old_library_version",
                    "Your code references the old library version (${pattern}). Please update your Jenkinsfile to use the new library version: *${deprecation.version}*", 
                    LocalDate.parse(deprecation.date_deadline)
                )
            }
        }
    }
    sh 'rm -f check-old-library-version.sh'
}
