// Section to Sync branches with master branch
// USAGE:


// def branchesToSync = ['demo', 'perftest']

// withPipeline(type, product, component) {	
//   syncBranchesWithMaster(branchesToSync)
// }

#!groovy
import uk.gov.hmcts.contino.AppPipelineConfig

def call(params) {
    AppPipelineConfig config = params.appPipelineConfig
    def product = params.product

    stageWithAgent("Sync Branches with Master", product) {
        if (!config.branchesToSyncWithMaster.isEmpty()) {
            withCredentials([this.steps.usernamePassword(credentialsId: 'jenkins-github-hmcts-api-token', usernameVariable: 'USERNAME', passwordVariable: 'BEARER_TOKEN')]) {
                
                sh '''
                    set -e
                    git remote set-url origin $(git config remote.origin.url | sed "s/github.com/${BEARER_TOKEN}@github.com/g")
                '''

                for (branch in config.branchesToSyncWithMaster) {
                    println('Syncing branch - ' + branch)

                    try {
                        sh '''git fetch origin '''+branch+''':'''+branch
                        sh '''git push --force origin HEAD:'''+branch
                        
                    } catch (err) {
                        println("Failed to update $branch branch.")
                        println(err.getMessage())
                        throw err
                    }
                }
            }
        }
    }
}