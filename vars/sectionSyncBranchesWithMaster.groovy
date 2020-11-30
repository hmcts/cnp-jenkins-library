#!groovy
import uk.gov.hmcts.contino.AppPipelineConfig

def call(params) {
    AppPipelineConfig config = params.appPipelineConfig
    def product = params.product

    stageWithAgent("Sync Branches with Master", product) {
        if (!config.branchesToSyncWithMaster.isEmpty()) {
            for (branch in config.branchesToSyncWithMaster) {
                println('Syncing branch - ' + branch)
            }
        }
    }
}