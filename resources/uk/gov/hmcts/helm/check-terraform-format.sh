#!/bin/bash
set -x

USER_NAME=${1}
BEARER_TOKEN=${2}
EMAIL_ID=${3}

def fmtTerraformcheck = sh(returnStatus:true, script: 'terraform fmt -check=true -recursive')
    echo "Terraform fmt exit status ${fmtTerraformcheck}"

    if (fmtExitCode != 0) {
    echo 'Terraform code is not formatted correctly'

    terraform fmt -recursive

    git fetch origin $BRANCH:$BRANCH
    git remote set-url origin $(git config remote.origin.url | sed "s/github.com/${USER_NAME}:${BEARER_TOKEN}@github.com/g ")

    git config --global user.name ${USER_NAME}:

    git config --global user.email ${GIT_APP_EMAIL_ID}

    git add $(find . -type f -name "*.tf")

    git commit -m "Updating Terraform Formatting"

    git push origin HEAD:$BRANCH

    error("Terraform was not formatted correctly, it has been reformatted and pushed back to your PR.")
    }

    set -e
    git remote set-url origin $(git config remote.origin.url | sed "s/github.com/${USER_NAME}:${BEARER_TOKEN}@github.com/g")
