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
                    """Your Jenkinsfile references an old or unpinned Jenkins library version.

Update it to use *Infrastructure@${deprecation.version}*, then check the migration guide and rollout tracker before raising a PR. Some repositories also need Key Vault or PostgreSQL module changes as part of this migration.

Migration guide: https://tools.hmcts.net/confluence/spaces/DTSPO/pages/1973509936/Jenkins+Library+Migration+Guide

Rollout tracker: https://tools.hmcts.net/confluence/spaces/DTSPO/pages/1973305638/Migration+rollout+tracker""",
                    LocalDate.parse(deprecation.date_deadline)
                )
            }
        }
    }
    sh 'rm -f check-old-library-version.sh'
}
