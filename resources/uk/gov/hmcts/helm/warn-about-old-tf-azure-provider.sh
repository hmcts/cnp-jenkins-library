#!/bin/bash

version_output=$(terraform --version)

terraform_version="$(echo "$version_output" | grep "Terraform v" | cut -d'v' -f 2)"
azurerm_version="$(echo "$version_output" | grep "azurerm" | cut -d'v' -f 3)"

terraform_major="$(echo "$terraform_version" | cut -d'.' -f 1)"
azurerm_major="$(echo "$azurerm_version" | cut -d'.' -f 1)"

if [[ "$azurerm_major" -eq "3" ]] && [[ "$terraform_major" -eq "1" ]] ; then
  echo "Terraform is at version - v${terraform_version}"
  echo "The Azurerm provider is at version v${azurerm_version}"
else
  echo "====================================================================================================="
  echo "=====  Please update your .terraform-version file, to the latest stable 1.x release            ======"
  echo "=====  The contents of this file will just be a Terraform version number, eg.                  ======"
  echo "=====                                                                                          ======"
  echo "=====  1.3.7                                                                                   ======"
  echo "=====                                                                                          ======"
  echo "=====  Ensure your required_providers block is on the latest 3.x version of azurerm, i.e       ======"
  echo "=====                                                                                          ======"
  echo "=====  terraform {                                                                             ======"
  echo "=====    required_providers {                                                                  ======"
  echo "=====      azurerm = {                                                                         ======"
  echo "=====       source  = \"hashicorp/azurerm\"                                                      ======"
  echo "=====       version = \"3.40\"                                                                   ======"
  echo "=====      }                                                                                   ======"
  echo "=====    }                                                                                     ======"
  echo "=====  }                                                                                       ======"
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
