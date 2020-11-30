#!groovy
import uk.gov.hmcts.contino.AppPipelineConfig

def call(params) {
    AppPipelineConfig config = params.appPipelineConfig
    def product = params.product

    stageWithAgent("Sync Branches with Master", product) {
        if (!config.branchesToSyncWithMaster.isEmpty()) {
            for (branch in config.branchesToSyncWithMaster) {
                println('Syncing branch - ' + branch)

                sh '''
                    set -e
                    git remote set-url origin $(git config remote.origin.url | sed "s/github.com/${BEARER_TOKEN}@github.com/g")
                '''

                try {
                    sh '''
                        git fetch origin '''+branch+''':''' +branch+'''
                        git push --force origin HEAD:'''+branch'''
                    '''
                } catch (err) {
                    echo "Failed to update $branch branch."
                    throw err
                }
            }
        }
    }
}