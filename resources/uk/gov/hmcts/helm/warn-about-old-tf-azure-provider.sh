#!/bin/bash
DEPENDENCY=${1}
REQUIRED_VERSION=${2}

version_output=$(terraform --version)

terraform_version="$(echo "$version_output" | grep "Terraform v" | cut -d'v' -f 2)"
azurerm_version="$(echo "$version_output" | grep "azurerm" | cut -d'v' -f 3)"

terraform_major="$(echo "$terraform_version" | cut -d'.' -f 1)"

echo "terraform major version is: $terraform_major"
echo "terraform version is: $terraform_version"
if [[ "$DEPENDENCY" == "terraform" ]] ; then
  echo "terraform required version is: $REQUIRED_VERSION"
fi

# if [[ "$DEPENDENCY" == "terraform" ]] ; then
  # check major version
#   if [[ "$terraform_major" -ge "$REQUIRED_VERSION" ]] ; then
#     echo "Terraform is at acceptable version - v${terraform_version}"
#   else
#       echo "====================================================================================================="
#       echo "=====  Please update your major terraform version to the latest stable release                 ======"
#       echo "=====  The contents of this file will just be a Terraform version number, eg.                  ======"
#       echo "=====                                                                                          ======"
#       echo "=====  1.3.7                                                                                   ======"
#       echo "=====                                                                                          ======"
#       echo "=====  Enable terraform in your renovate config, either by extending config:base or enabling   ======"
#       echo "=====  the terraform manager.                                                                  ======"
#       echo "=====                                                                                          ======"
#       echo "=====  See:                                                                                    ======"
#       echo "=====   - https://github.com/hmcts/spring-boot-template/pull/412                               ======"
#       echo "=====   - https://github.com/hmcts/draft-store/pull/1168                                       ======"
#       echo "=====   - https://github.com/hmcts/spring-boot-template/blob/master/.github/renovate.json      ======"
#       echo "====================================================================================================="
#       exit 1
#     fi
# elif [[ "$DEPENDENCY" == "providers" ]] ; then
#   echo
  # if [[ "$terraform_major" -ge "$REQUIRED_VERSION" ]] ; then
  #   echo "Terraform is at acceptable version - v${terraform_version}"
  # else
  #     echo "====================================================================================================="
  #     echo "=====  Please update your major terraform version to the latest stable release                 ======"
  #     echo "=====  The contents of this file will just be a Terraform version number, eg.                  ======"
  #     echo "=====                                                                                          ======"
  #     echo "=====  1.3.7                                                                                   ======"
  #     echo "=====                                                                                          ======"
  #     echo "=====  Enable terraform in your renovate config, either by extending config:base or enabling   ======"
  #     echo "=====  the terraform manager.                                                                  ======"
  #     echo "=====                                                                                          ======"
  #     echo "=====  See:                                                                                    ======"
  #     echo "=====   - https://github.com/hmcts/spring-boot-template/pull/412                               ======"
  #     echo "=====   - https://github.com/hmcts/draft-store/pull/1168                                       ======"
  #     echo "=====   - https://github.com/hmcts/spring-boot-template/blob/master/.github/renovate.json      ======"
  #     echo "====================================================================================================="
  #     exit 1
  #   fi
# else
#   echo "Dependency value not recognised, terraform versions not checked"
# fi
