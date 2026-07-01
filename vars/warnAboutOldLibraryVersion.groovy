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
            int status = sh(
                script: """
                chmod +x check-old-library-version.sh
                ./check-old-library-version.sh '${pattern}' '${deprecation.version}' '${deprecation.date_deadline}'
                """,
                returnStatus: true
            )

            if (status != 0) {
                String warningMessage = """Your Jenkinsfile references an old or unpinned Jenkins library version.

Update it to use *Infrastructure@${deprecation.version}*, then check the migration guide and rollout tracker before raising a PR. Some repositories also need Key Vault or PostgreSQL module changes as part of this migration.

Migration guide: https://tools.hmcts.net/confluence/spaces/DTSPO/pages/1973509936/Jenkins+Library+Migration+Guide

Rollout tracker: https://tools.hmcts.net/confluence/spaces/DTSPO/pages/1973305638/Migration+rollout+tracker"""
                LocalDate deprecationDate = LocalDate.parse(deprecation.date_deadline)

                "Old library version detected. Update to Infrastructure@${deprecation.version}."

                try {
                    WarningCollector.addPipelineWarning(
                        "old_library_version",
                        warningMessage,
                        deprecationDate
                    )
                } catch (RuntimeException ignored) {
                    echo "${warningMessage} This change is enforced from ${deprecationDate.format(WarningCollector.DATE_FORMATTER)}"
                }
            }
        }
    }
    sh 'rm -f check-old-library-version.sh'
}