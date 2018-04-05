#!/bin/bash

#Management network details
networkA=$1
networkB="core-infra-vnet-$3"

resourceA=$1
subscriptionA=$2
nameA="A-CNP${networkA}toCNP${networkB}"

networkB="core-infra-vnet-$3"
resourceB="core-infra-$3"
subscriptionB=$4
nameB="B-CNP${networkB}toCNP${networkA}"

env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$SUBSCRIPTION_NAME az account set --subscription ${subscriptionA}

deletedA=false
deletedB=false

# Check if VNET peering exists & is in connected state for VNetA
if [ "$(AZURE_CONFIG_DIR=/opt/jenkins/.azure-$SUBSCRIPTION_NAME az network vnet peering show --name ${nameA} --resource-group ${resourceA} --vnet-name ${networkA} --query peeringState)" != '"Connected"' ] ; then
  AZURE_CONFIG_DIR=/opt/jenkins/.azure-$SUBSCRIPTION_NAME az network vnet peering delete \
   --name ${nameA} \
   --resource-group ${resourceA} \
   --vnet-name  ${networkA}
   deletedA=true
fi

# Check if VNET peering exists & is in connected state for VNetB
if [ "$(AZURE_CONFIG_DIR=/opt/jenkins/.azure-$SUBSCRIPTION_NAME az network vnet peering show --name ${nameB} --resource-group ${resourceB} --vnet-name ${networkB} --query peeringState)" != '"Connected"' ]  ; then
  AZURE_CONFIG_DIR=/opt/jenkins/.azure-$SUBSCRIPTION_NAME az network vnet peering delete \
   --name ${nameB} \
   --resource-group ${resourceB} \
   --vnet-name  ${networkB}
   deletedB=true
fi

# Peer VNetA to VNetB
if [ $deletedA ]; then
  env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$SUBSCRIPTION_NAME az network vnet peering create \
   --name ${nameA} \
   --resource-group ${resourceA} \
   --vnet-name ${networkA} \
   --remote-vnet-id /subscriptions/${subscriptionB}/resourceGroups/${resourceB}/providers/Microsoft.Network/VirtualNetworks/${networkB} \
   --allow-vnet-access
  env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$SUBSCRIPTION_NAME az network vnet peering update \
   --vnet-name ${networkA} \
   --name ${nameA} \
   --resource-group ${resourceA} \
   --set allowForwardedTraffic==true
  env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$SUBSCRIPTION_NAME az network vnet peering update \
   --vnet-name ${networkA} \
   --name ${nameA} \
   --resource-group ${resourceA} \
   --set allowVirtualNetworkAccess==true
  env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$SUBSCRIPTION_NAME az network vnet peering list \
   --resource-group ${resourceA} \
   --vnet-name ${networkA} \
   --output table
  env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$SUBSCRIPTION_NAME az account set --subscription ${subscriptionB}
fi

# Peer VNetB to VNetA
if [ $deletedB ]; then
  env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$SUBSCRIPTION_NAME az network vnet peering create \
    --name ${nameB} \
    --resource-group ${resourceB} \
    --vnet-name ${networkB} \
    --remote-vnet-id /subscriptions/${subscriptionA}/resourceGroups/${resourceA}/providers/Microsoft.Network/VirtualNetworks/${networkA} \
    --allow-vnet-access
  env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$SUBSCRIPTION_NAME az network vnet peering update \
    --vnet-name ${networkB} \
    --name ${nameB} \
    --resource-group ${resourceB} \
    --set allowForwardedTraffic==true
  env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$SUBSCRIPTION_NAME az network vnet peering update \
    --vnet-name ${networkB} \
    --name ${nameB} \
    --resource-group ${resourceB} \
    --set allowVirtualNetworkAccess==true
  env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$SUBSCRIPTION_NAME az network vnet peering list \
    --resource-group ${resourceB} \
    --vnet-name ${networkB} \
    --output table
fi