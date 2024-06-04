#!/bin/bash


# This function takes two version numbers following semantic versioning i.e. 1.4.5 and returns
# 0 - Versions that are the same
# 1 - First version argument is less than the second
# 2 - First version argument is greater than the second
# You should pass the version you wish to compare against a desired version as the first arg, and the desired
# version as the second arg
compare_versions() {
  # Versions are the same   
  if [[ "$1" == "$2" ]]; then
      return 0
  fi

  local sorted_version_list=$(printf "%s\n%s" "$1" "$2" | sort -V)
  local first_version=$(echo "$sorted_version_list" | head -n 1)

  if [[ "$first_version" == "$1" ]]; then
      return 1 # below required version
  else
      return 2 # above required version
  fi
}

DEPENDENCY=${1}
REQUIRED_VERSION=${2}

terraform_version=$(terraform --version --json | jq -r '.terraform_version')
terraform_providers=$(terraform --version --json | jq -r '.provider_selections')
echo "terraform version is: $terraform_version"


if [[ "$DEPENDENCY" == "terraform" ]] ; then
  echo "Required terraform version is: $REQUIRED_VERSION"
  compare_versions "$terraform_version" "$REQUIRED_VERSION"
  result=$?

  if [[ $result != 1 ]] ; then
    echo "Terraform is at acceptable version: $terraform_version"
  else
    echo "====================================================================================================="
    echo "=====  Please update your major terraform version to the latest stable release                 ======"
    echo "=====  The contents of this file will just be a Terraform version number, eg.                  ======"
    echo "=====                                                                                          ======"
    echo "=====  1.8.4                                                                                   ======"
    echo "=====                                                                                          ======"
    echo "=====  Enable terraform in your renovate config, either by extending config:base or enabling   ======"
    echo "=====  the terraform manager.                                                                  ======"
    echo "=====                                                                                          ======"
    echo "=====  See:                                                                                    ======"
    echo "=====   - https://github.com/hmcts/spring-boot-template/pull/412                               ======"
    echo "=====   - https://github.com/hmcts/draft-store/pull/1168                                       ======"
    echo "=====   - https://github.com/hmcts/spring-boot-template/blob/master/.github/renovate.json      ======"
    echo "====================================================================================================="
    exit 1
  fi
else
  echo "Terraform provider configuration detected: $terraform_providers"

  echo "Terraform provider name: $DEPENDENCY"
  echo "Required version of provider: $REQUIRED_VERSION ..."
  provider_version=$(echo $terraform_providers | jq --arg p "${provider_name}" '.[$p]')

  # Returns "null" ... if provider not found in nagger map
  if [[ "$provider_version" != "null" ]]; then
    echo "Found matching provider in map for $DEPENDENCY, checking:"
    compare_versions "$provider_version" "$REQUIRED_VERSION"
    result=$?

      if [[ $result != 1 ]] ; then
        echo "Terraform provider $DEPENDENCY is at acceptable version: $provider_version"
      else
        echo "====================================================================================================="
        echo "=====  Please update your version for provider for $DEPENDENCY to $REQUIRED_VERSION            ======"
        echo "====================================================================================================="
        exit 1
      fi
  fi  
fi
