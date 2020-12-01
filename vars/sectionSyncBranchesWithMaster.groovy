#!groovy
import uk.gov.hmcts.contino.AppPipelineConfig

def call(params) {
    AppPipelineConfig config = params.appPipelineConfig
    def product = params.product

    stageWithAgent("Sync Branches with Master", product) {
        if (!config.branchesToSyncWithMaster.isEmpty()) {
            withCredentials([this.steps.usernamePassword(credentialsId: 'jenkins-github-hmcts-api-token', usernameVariable: 'USERNAME', passwordVariable: 'BEARER_TOKEN')]) {
                
                for (branch in config.branchesToSyncWithMaster) {
                    println('Syncing branch - ' + branch)

                    sh '''
                        set -e
                        git remote set-url origin $(git config remote.origin.url | sed "s/github.com/${BEARER_TOKEN}@github.com/g")
                    '''

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

            // for (branch in config.branchesToSyncWithMaster) {
            //     println('Syncing branch - ' + branch)

            //     sh '''
            //         set -e
            //         git remote set-url origin $(git config remote.origin.url | sed "s/github.com/${BEARER_TOKEN}@github.com/g")
            //     '''

            //     try {
            //         sh '''
            //             git fetch origin '''+branch+''':''' +branch+'''
            //             git push --force origin HEAD:'''+branch'''
            //         '''
            //     } catch (err) {
            //         echo "Failed to update $branch branch."
            //         throw err
            //     }
            // }
        }
    }
}