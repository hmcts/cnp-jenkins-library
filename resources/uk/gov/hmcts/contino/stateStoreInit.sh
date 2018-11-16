#!/usr/bin/env bash

__rg=$1
__sa=$2
__container=$3
__location=$4
__subscription=$5

if [ -z $__rg ]; then
    fail "Resource Group name not provided!"
fi

if [ -z $__sa ]; then
    fail "Storage Account name not provided!"
fi

if [ -z $__container ]; then
    fail "Storage Account Container name not provided!"
fi

if [ -z $__location ]; then
    fail "Location not provided!"
fi

# check if the storage account exists. Creates it if not.
if  ! "$(env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$__subscription az group exists --name $__rg)" ; then

    __isCreated="$(env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$__subscription az group create --name $__rg --location $__location --output tsv --query properties.provisioningState)"

    if [ "${__isCreated}" == "Succeeded" ] ; then
        echo "The resource $__rg has been created with no error"
    else
        fail "The resource group $__rg hasn't been created successfully"
        return 0
    fi
else
    echo "The resource $__rg already exists."
fi


__saState="$(env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$__subscription az storage account show --name $__sa --resource-group $__rg --output tsv --query provisioningState)"

if [ -z "${__saState}" ] ; then
    # create a new storage account
    # check if the storage account name s a valid format
    __saCheck="$(env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$__subscription az storage account check-name --name $__sa --output tsv)"

# TODO These checks must be reviewed for when the sa is NOT available
    __isAvailable="$(echo $__saCheck | cut -f2 -d ' ')"
    __message="$(echo $__saCheck | cut -f1 -d ' ')"
    __reason="$(echo $__saCheck | cut -f3 -d ' ')"

    if [ "${__isAvailable,,}" = "true" ] ; then
       env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$__subscription az storage account create --name $__sa \
          --resource-group $__rg \
          --sku Standard_LRS \
          --encryption-services blob \
          --kind StorageV2 \
          --location $__location
    else
        echo -e "${__message} \t ${__reason}"
        exit 1
    fi

else
    echo "The storage account $__sa in the resource group $__rg already exists."
fi

__sa_key="$(env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$__subscription az storage account keys list --account-name $__sa --resource-group $__rg --output tsv --query '[1].value')"

# check if the storage account doesn't exists
__saContExists="$(env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$__subscription az storage container exists --account-name $__sa --account-key $__sa_key --name $__container --query exists)"

if [ "${__saContExists,,}" = "false" ] ; then
    env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$__subscription az storage container create --name $__container \
         --account-key $__sa_key \
         --account-name $__sa \
         --fail-on-exist \
         --public-access off \
         --output json
else
    echo "The storage account container $__container already exists."
fi
