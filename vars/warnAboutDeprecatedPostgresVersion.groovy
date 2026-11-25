import uk.gov.hmcts.pipeline.DeprecationConfig
import uk.gov.hmcts.pipeline.deprecation.WarningCollector

import java.time.LocalDate

def call(String repoUrl = env.GIT_URL) {
    def deprecation = new DeprecationConfig(this).getDeprecationConfig(repoUrl).database?.postgresql_flexible_server_sku_v3
    if (!deprecation) {
        throw new IllegalStateException("Missing database.postgresql_flexible_server_sku_v3 deprecation configuration")
    }

    writeFile(
        file: 'check-deprecated-postgres-version.sh',
        text: libraryResource('uk/gov/hmcts/infrastructure/check-deprecated-postgres-version.sh')
    )

    try {
        sh 'chmod +x check-deprecated-postgres-version.sh'
        def affectedResources = sh(
            script: "./check-deprecated-postgres-version.sh tfplan",
            returnStdout: true
        ).trim()

        if (affectedResources) {
            WarningCollector.addPipelineWarning(
                'deprecated_postgresql_flexible_server_sku_v3',
                "PostgreSQL Flexible Server SKU v3 is deprecated. Affected Terraform resources (address and SKU): ${affectedResources.replace('\n', ', ')}. Upgrade to a v5 SKU by 30 June 2027. See ${deprecation.upgrade_guide}",
                LocalDate.parse(deprecation.date_deadline)
            )
        }
    } finally {
        sh 'rm -f check-deprecated-postgres-version.sh'
    }
}
