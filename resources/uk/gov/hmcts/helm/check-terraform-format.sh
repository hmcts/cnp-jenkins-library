def checkTerraformFormatting() {
    def fmtExitCode = sh(returnStatus: true, script: 'terraform fmt -check=true -recursive')
    echo "Terraform fmt exit status: ${fmtExitCode}"
    if (fmtExitCode != 0) {
        echo 'Terraform code is not formatted correctly'
        // Format the Terraform code recursively
        sh 'terraform fmt -recursive'
        // Commit the formatting changes
        gitFetch()
        updateGitRemoteUrl()
        configureGitUser()
        commitFormattingChanges()
        pushChangesToBranch()
        error("Terraform was not formatted correctly, it has been reformatted and pushed back to your Pull Request.")
    }
}
def gitFetch() {
    sh "git fetch origin ${env.BRANCH_NAME}:${env.BRANCH_NAME}"
}
def updateGitRemoteUrl() {
    withCredentials([string(credentialsId: 'github-token', variable: 'GITHUB_TOKEN')]) {
        sh "git remote set-url origin $(git config remote.origin.url | sed 's/github.com/${env.GIT_USERNAME}:${GITHUB_TOKEN}@github.com/g')"
    }
}
def configureGitUser() {
    sh "git config --global user.name ${env.GIT_USERNAME}"
    sh "git config --global user.email ${env.GIT_EMAIL}"
}
def commitFormattingChanges() {
    sh "git add \$(find . -type f -name '*.tf')"
    sh "git commit -m 'Updating Terraform Formatting'"
}
def pushChangesToBranch() {
    sh "git push origin HEAD:${env.BRANCH_NAME}"
}