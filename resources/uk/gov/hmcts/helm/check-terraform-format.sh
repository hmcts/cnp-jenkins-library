#!/bin/bash
set -x

BRANCH=${1}
USER_NAME=${2}
BEARER_TOKEN=${3}
EMAIL_ID=${4}

checkTerraformFormat() {
  fmtExitCode=$(terraform fmt -check=true -recursive 2>&1)
  fmtOutput=$(terraform fmt -check=true -recursive)
  echo "Terraform fmt exit status: ${fmtExitCode}"
  echo "Terraform fmt output:\n${fmtOutput}"
  echo "Terraform fmt exit status: ${fmtExitCode}"
  if [[ $fmtExitCode -ne 0 ]]; then
    echo 'Terraform is not formatted correctly'

    echo 'Current working directory:'
    pwd

    echo 'Files in the current directory:'
    ls -la

    # Format the Terraform code recursively
    terraform fmt -recursive

    # Committing the formatting changes
    # gitFetch
    # updateGitRemoteUrl
    # configureGitUser
    # commitFormattingChanges
    # pushChangesToBranch

    echo "Terraform was not formatted correctly, it has been reformatted and pushed back to your Pull Request."
  else
    echo 'Terraform code is formatted correctly'
  fi
}

git fetch origin $BRANCH:$BRANCH
git remote set-url origin $(git config remote.origin.url | sed "s/github.com/${USER_NAME}:${BEARER_TOKEN}@github.com/g")

# configureGitUser
git config --global user.name ${USER_NAME} 
git config --global user.email ${GIT_APP_EMAIL_ID}


# commitFormattingChanges
git add "$(find . -type f -name '*.tf')"
git commit -m "Updating Terraform Formatting"


# PushChangesToBranch    
git push origin HEAD:BRANCH