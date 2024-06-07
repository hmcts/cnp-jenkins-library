#!/bin/bash
set -x

BRANCH=${1}
USER_NAME=${2}
BEARER_TOKEN=${3}
EMAIL_ID=${4}

checkTerraformFormat() {
  fmtOutput=$(terraform fmt -check=true -recursive)

  echo "Terraform fmt output:\n${fmtOutput}"

  if [[ -n $fmtOutput ]]; then
    echo 'Terraform is not formatted correctly'

    echo 'Current working directory:'
    pwd

    echo 'Files in the current directory:'
    ls -la

    # Format the Terraform code recursively
    terraform fmt -recursive

    echo "Terraform was not formatted correctly, it has been reformatted and pushed back to your Pull Request."
  else
    echo 'Terraform code is formatted correctly'
  fi
}

  if [ -n $fmtOutput ]; then

git fetch origin $BRANCH:$BRANCH
git remote set-url origin $(git config remote.origin.url | sed "s/github.com/${USER_NAME}:${BEARER_TOKEN}@github.com/g")

# configureGitUser
git config --global user.name ${USER_NAME} 
git config --global user.email ${GIT_APP_EMAIL_ID}

checkTerraformFormat

# commitFormattingChanges
git add .
git commit -m "Updating Terraform Formatting"


# PushChangesToBranch    
git push origin HEAD:$BRANCH
fi