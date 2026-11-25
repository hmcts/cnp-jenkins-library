import uk.gov.hmcts.pipeline.DeprecationConfig
import uk.gov.hmcts.pipeline.deprecation.WarningCollector

def call(String repoUrl = env.GIT_URL) {
    def deprecation = new DeprecationConfig(this).getDeprecationConfig(repoUrl).database?.postgresql_v3_sku
    if (!deprecation?.date_deadline || !deprecation?.upgrade_guide || !deprecation?.message) {
        throw new IllegalStateException("Missing database.postgresql_v3_sku deprecation configuration")
    }

    writeFile(
        file: 'check-deprecated-postgres-sku.sh',
        text: libraryResource('uk/gov/hmcts/infrastructure/check-deprecated-postgres-sku.sh')
    )

    try {
        sh 'chmod +x check-deprecated-postgres-sku.sh'
        def affectedResources = sh(
            script: './check-deprecated-postgres-sku.sh tfplan',
            returnStdout: true
        ).trim()

        if (affectedResources) {
            String message = "${deprecation.message} Deadline: ${deprecation.date_deadline}. " +
                "Upgrade guidance: ${deprecation.upgrade_guide}. " +
                "Affected Terraform resources (address, state and SKU): ${affectedResources.replace('\n', ', ')}"
            echo "WARNING: ${message}"
            WarningCollector.addPipelineWarning('deprecated_postgresql_v3_sku', message, null, false, false)
        }
    } finally {
        sh 'rm -f check-deprecated-postgres-sku.sh'
    }
}
