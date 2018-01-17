#!/bin/bash

#Management network details
networkA=$1
resourceA=$1
subscriptionA=$2
nameA="CNPMgmtProdToCNPAppProd"

networkB="core-infra-vnet-$3"
resourceB="core-infra-$3"
subscriptionB=$4
nameB="CNPAppProdToCNPMgmtProd"

az account set --subscription ${subscriptionA}

# Peer VNetA to VNetB
az network vnet peering create \
 --name ${nameA} \
 --resource-group ${resourceA} \
 --vnet-name ${networkA} \
 --remote-vnet-id /subscriptions/${subscriptionB}/resourceGroups/${resourceB}/providers/Microsoft.Network/VirtualNetworks/${networkB} \
 --allow-vnet-access
az network vnet peering update \
 --vnet-name ${networkA} \
 --name ${nameA} \
 --resource-group ${resourceA} \
 --set allowForwardedTraffic==true
az network vnet peering update \
 --vnet-name ${networkA} \
 --name ${nameA} \
 --resource-group ${resourceA} \
 --set allowVirtualNetworkAccess==true
az network vnet peering list \
 --resource-group ${resourceA} \
 --vnet-name ${networkA} \
 --output table
az account set --subscription ${subscriptionB}

# Peer VNetB to VNetA
az network vnet peering create \
  --name ${nameB} \
  --resource-group ${resourceB} \
  --vnet-name ${networkB} \
  --remote-vnet-id /subscriptions/${subscriptionA}/resourceGroups/${resourceA}/providers/Microsoft.Network/VirtualNetworks/${networkA} \
  --allow-vnet-access
az network vnet peering update \
  --vnet-name ${networkB} \
  --name ${nameB} \
  --resource-group ${resourceB} \
  --set allowForwardedTraffic==true
az network vnet peering update \
  --vnet-name ${networkB} \
  --name ${nameB} \
  --resource-group ${resourceB} \
  --set allowVirtualNetworkAccess==true
az network vnet peering list \
  --resource-group ${resourceB} \
  --vnet-name ${networkB} \
  --output table