import uk.gov.hmcts.pipeline.DeprecationConfig
import uk.gov.hmcts.pipeline.deprecation.DeprecationWarning
import uk.gov.hmcts.pipeline.deprecation.WarningCollector

def call(String repoUrl, String environment) {
    def deprecation = new DeprecationConfig(this).getDeprecationConfig(repoUrl).database?.postgresql_v3_sku
    if (!deprecation?.sku || !deprecation?.message) {
        throw new IllegalStateException('Missing database.postgresql_v3_sku deprecation configuration')
    }
    if (!(deprecation.sku instanceof String) || !(deprecation.sku ==~ /v[0-9]+/)) {
        throw new IllegalStateException('Invalid database.postgresql_v3_sku.sku; expected a generation such as v3')
    }
    writeFile(
        file: 'check-deprecated-postgres-sku.sh',
        text: libraryResource('uk/gov/hmcts/infrastructure/check-deprecated-postgres-sku.sh')
    )

    try {
        sh 'chmod +x check-deprecated-postgres-sku.sh'
        def affectedResources = sh(
            script: "./check-deprecated-postgres-sku.sh tfplan '${deprecation.sku}'",
            returnStdout: true
        ).trim()

        if (affectedResources) {
            String key = 'deprecated_postgresql_v3_sku'
            String details = "Environment: ${environment}; affected Terraform resources (address and SKU): ${affectedResources.replace('\n', ', ')}"
            int warningIndex = WarningCollector.pipelineWarnings.findIndexOf { it.warningKey == key }
            if (warningIndex < 0) {
                WarningCollector.addPipelineWarning(key, "${deprecation.message}\n${details}", null, false)
            } else {
                def existing = WarningCollector.pipelineWarnings[warningIndex]
                WarningCollector.pipelineWarnings[warningIndex] =
                    new DeprecationWarning(key, "${existing.warningMessage}\n${details}", existing.deprecationDate)
            }
            echo "WARNING: ${deprecation.message}\n${details}"
        }
    } finally {
        sh 'rm -f check-deprecated-postgres-sku.sh'
    }
}
