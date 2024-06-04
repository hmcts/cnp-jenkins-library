#!/bin/bash

checkTerraformFormat() {
call() {   

    # writeFile file: 'check-terraform-format.sh', text: libraryResource('uk/gov/hmcts/helm/check-terraform-format.sh')
    
    fmtExitCode = sh(returnStatus: true, script: 'terraform fmt -check=true -recursive')
    fmtOutput = sh(returnStdout: true, script: 'terraform fmt -check=true -recursive')
    echo "Terraform fmt exit status: ${fmtExitCode}"
    echo "Terraform fmt output:\n${fmtOutput}"
    echo "Terraform fmt exit status: ${fmtExitCode}"
    if (fmtExitCode != 0) {
        echo 'Terraform is not formatted correctly'

        echo 'Current working directory:'
        sh 'pwd'

        echo 'Files in the current directory:'
        sh 'ls -la'

        // Format the Terraform code recursively
        sh 'terraform fmt -recursive'

        // Committing the formatting changes
        gitFetch()
        updateGitRemoteUrl()
        configureGitUser()
        commitFormattingChanges()
        pushChangesToBranch()

        error("Terraform was not formatted correctly, it has been reformatted and pushed back to your Pull Request.")
    } else {
        echo 'Terraform code is formatted correctly'
    }
}

gitFetch() {
    sh "git fetch origin ${env.BRANCH_NAME}:${env.BRANCH_NAME}"
}

updateGitRemoteUrl() {
    withCredentials([string(credentialsId: 'github-token', variable: 'GITHUB_TOKEN')]) {
        sh "git remote set-url origin $(git config remote.origin.url | sed 's/github.com/${env.GIT_USERNAME}:${GITHUB_TOKEN}@github.com/g')"
    }
}
configureGitUser() {
    sh "git config --global user.name ${env.GIT_USERNAME}"
    sh "git config --global user.email ${env.GIT_EMAIL}"
}

commitFormattingChanges() {
    sh "git add \$(find . -type f -name '*.tf')"
    sh "git commit -m 'Updating Terraform Formatting'"
}

pushChangesToBranch() {
    sh "git push origin HEAD:${env.BRANCH_NAME}"
} 
}  