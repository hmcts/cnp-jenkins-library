#!/usr/bin/env bash

__rg=$1
__sa=$2
__container=$3
__location=$4

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
if  ! "$(az group exists --name $__rg)" ; then

    __isCreated="$(az group create --name $__rg --location $__location --output json | jq -r .properties.provisioningState)"

    if [ "${__isCreated}" == "Succeeded" ] ; then
        echo "The resource $__rg has been created with no error"
    else
        fail "The resource group $__rg hasn't been created successfully"
        return 0
    fi
else
    echo "The resource $__rg already exists."
fi


__saState="$(az storage account show --name $__sa --resource-group $__rg --output json | jq -r .provisioningState)"

if [ -z "${__saState}" ] ; then
    # create a new storage account
        # check if the storage account name s a valid format
    __saCheck="$(az storage account check-name --name $__sa --output json )"

    __isAvailable=`echo "${__saCheck}" | jq -r .nameAvailable`
    __message=`echo "${__saCheck}" | jq -r .message`
    __reason=`echo "${__saCheck}" | jq -r .reason`

    if [ "$__isAvailable" = "true" ] ; then
       az storage account create --name $__sa \
          --resource-group $__rg \
          --sku Standard_LRS \
          --encryption-services blob \
          --kind Storage \
          --location $__location
    else
        echo -e "${__message} \t ${__reason}"
        exit 1
    fi

else
    echo "The storage account $__sa in the resource group $__rg already exists."
fi

__sa_key="$(az storage account keys list --account-name $__sa --resource-group $__rg --output json | jq -r '.[1].value')"

# check if the storage account doesn't exists
__saContExists="$(az storage container exists --account-name $__sa --account-key $__sa_key --name $__container | jq -r .exists)"

if [ "$__saContExists" = "false" ] ; then
    az storage container create --name $__container \
         --account-key $__sa_key \
         --account-name $__sa \
         --fail-on-exist \
         --public-access off \
         --output json
else
    echo "The storage account container $__container already exists."
fi
