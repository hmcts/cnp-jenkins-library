#!/bin/bash
set -x

CHART_DIRECTORY=${product}-${component}

echo "Checking for flux image repository"

az aks get-credentials --resource-group ${resourceGroup} --name ${clusterName} --subscription  ${aksSubscription} -a 

kubectl config get-contexts

flux get image repository $CHART_DIRECTORY
