import uk.gov.hmcts.pipeline.DeprecationConfig
import uk.gov.hmcts.pipeline.deprecation.WarningCollector

import java.time.LocalDate

def call(String repoUrl = env.GIT_URL) {
    def deprecation = new DeprecationConfig(this).getDeprecationConfig(repoUrl).database?.postgresql
    if (!deprecation) {
        throw new IllegalStateException("Missing database.postgresql deprecation configuration")
    }

    writeFile(
        file: 'check-deprecated-postgres-version.sh',
        text: libraryResource('uk/gov/hmcts/infrastructure/check-deprecated-postgres-version.sh')
    )

    try {
        sh 'chmod +x check-deprecated-postgres-version.sh'
        def affectedResources = sh(
            script: "./check-deprecated-postgres-version.sh '${deprecation.version}' tfplan",
            returnStdout: true
        ).trim()

        if (affectedResources) {
            WarningCollector.addPipelineWarning(
                'deprecated_postgresql_version',
                "${deprecation.message} Affected Terraform resources (address and version): ${affectedResources.replace('\n', ', ')}",
                LocalDate.parse(deprecation.date_deadline)
            )
        }
    } finally {
        sh 'rm -f check-deprecated-postgres-version.sh'
    }
}
