#!/bin/bash
set -x

CHART_DIRECTORY=${1}-${2}

echo "Checking for flux image repository"

env AZURE_CONFIG_DIR=/opt/jenkins/.azure-jenkins az login --identity

env AZURE_CONFIG_DIR=/opt/jenkins/.azure-jenkins az aks get-credentials --resource-group ${3} --name ${4} --subscription ${5} -a 

kubectl config get-contexts

flux get image repository $CHART_DIRECTORY
