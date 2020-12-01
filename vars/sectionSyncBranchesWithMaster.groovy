#!groovy

// Section to Sync branches with master branch
// USAGE:

// def branchesToSync = ['demo', 'perftest']

// withPipeline(type, product, component) {	
//   syncBranchesWithMaster(branchesToSync)
// }

import uk.gov.hmcts.contino.AppPipelineConfig

def call(params) {
    AppPipelineConfig config = params.appPipelineConfig
    def product = params.product
    def credentialsId = env.GIT_CREDENTIALS_ID

    stageWithAgent("Sync Branches with Master", product) {
        if (!config.branchesToSyncWithMaster.isEmpty()) {
            
            withCredentials([usernamePassword(credentialsId: credentialsId, passwordVariable: 'BEARER_TOKEN', usernameVariable: 'USER_NAME')]) {
                sh '''
                    set -e
                    git remote set-url origin $(git config remote.origin.url | sed "s/github.com/${USER_NAME}:${BEARER_TOKEN}@github.com/g")

                '''

                for (branch in config.branchesToSyncWithMaster) {
                    echo "Syncing branch - ${branch}"

                    try {
                        sh """
                         git fetch origin ${branch}:${branch}
                         git push --force origin HEAD:${branch}
                        """
                        
                    } catch (err) {
                        echo "Failed to update $branch branch."
                        echo err.getMessage()
                        throw err
                    }

                    echo "Sync completed for - ${branch}"
                }
            }
        }
    }
}
