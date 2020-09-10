#!/bin/bash

version_output=$(terraform --version)

terraform_version="$(echo "$version_output" | grep "Terraform v" | cut -d'v' -f 2)"
azurerm_version="$(echo "$version_output" | grep "azurerm" | cut -d'v' -f 3)"

terraform_minor="$(echo "$terraform_version" | cut -d'.' -f 2)"
azurerm_major="$(echo "$azurerm_version" | cut -d'.' -f 1)"

if [[ "$azurerm_major" != "1" && "$azurerm_major" != "0" ]] && [[ "$terraform_minor" != "11" ]] ; then
  echo "Terraform is at version - v${terraform_version}"
  echo "The Azurerm provider is at version v${azurerm_version}"
else
  echo "====================================================================================================="
  echo "=====  Please add a .terraform-version file, without this it will fall back to 0.11.7          ======"
  echo "=====  but 0.11.7 is incompatible with Azurerm 2.x                                             ======"
  echo "=====  The contents of this file will just be a Terraform version number, eg.                  ======"
  echo "=====                                                                                          ======"
  echo "=====  0.13.2                                                                                  ======"
  echo "=====                                                                                          ======"
  echo "=====  Please ensure this provider block is set:                                               ======"
  echo "=====                                                                                          ======"
  echo "=====  provider \"azurerm\" {                                                                  ======"
  echo "=====    features {}                                                                           ======"
  echo "=====  }                                                                                       ======"
  echo "=====                                                                                          ======"
  echo "=====  Change the keyvault module branch to be azurermv2:                                      ======"
  echo "=====  Eg. source = \"git@github.com:hmcts/cnp-module-key-vault?ref=azurermv2\"                ======"
  echo "=====                                                                                          ======"
  echo "=====  Add a required_providers block which specifies minimum version of azurerm, i.e          ======"
  echo "=====                                                                                          ======"
  echo "=====  terraform {                                                                             ======"
  echo "=====    required_providers {                                                                  ======"
  echo "=====      azurerm = {                                                                         ======"
  echo "=====       source  = \"hashicorp/azurerm\"                                                    ======"
  echo "=====       version = \"~> 2.25\"                                                              ======"
  echo "=====      }                                                                                   ======"
  echo "=====    }                                                                                     ======"
  echo "=====  }                                                                                       ======"
  echo "=====                                                                                          ======"
  echo "=====  For examples, see: https://github.com/hmcts/rpe-pdf-service/pull/281                    ======"
  echo "=====  and https://github.com/hmcts/rpe-shared-infrastructure/pull/11                          ======"
  echo "=====                                                                                          ======"
  echo "=====  We will start enforcing this as the key vault module needs to be updated                ======"
  echo "=====                                                                                          ======"
  echo "====================================================================================================="
  exit 1
fi
