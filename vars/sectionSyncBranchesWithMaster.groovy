#!groovy

// Section to Sync branches with master branch
// USAGE:

// def branchesToSync = ['demo', 'perftest']

// withPipeline(type, product, component) {	
//   syncBranchesWithMaster(branchesToSync)
// }

def call(params) {
    def branchesToSync = params.branchestoSync != null ? params.branchestoSync : ['ithc', 'demo', 'perftest']
    def product = params.product
    def credentialsId = env.GIT_CREDENTIALS_ID

    if (!branchesToSync.isEmpty()) {
        stageWithAgent("Sync Branches with Master", product) {
            withCredentials([usernamePassword(credentialsId: credentialsId, passwordVariable: 'BEARER_TOKEN', usernameVariable: 'USER_NAME')]) {
                sh '''
                    set -e
                    git remote set-url origin $(echo $ORIGINAL_REMOTE_URL | sed "s/github.com/${USER_NAME}:${BEARER_TOKEN}@github.com/g")
                '''

                for (branch in branchesToSync) {
                    def status = sh(returnStatus: true, script: "git ls-remote --exit-code --heads origin $branch")
                    def exists = 0
                    if (status == exists) {    
                        try {
                            echo "Syncing branch - ${branch}"

                            sh """
                                git fetch origin ${branch}:${branch}
                                git push --force origin HEAD:refs/heads/${branch}
                            """

                            echo "Sync completed for branch - ${branch}"
                        
                        } catch (err) {
                            echo "Failed to update branch - $branch"
                            echo err.getMessage()
                        }
                    } else {
                        echo "Sync didn't run as $branch doesn't exist"
                    }
                }
            }
        }
    }
}