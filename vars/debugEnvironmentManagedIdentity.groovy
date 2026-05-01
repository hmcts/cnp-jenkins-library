def call(String environment, String agentLabel) {
  sh(
    label: "Debug managed identity for ${environment}",
    script: """#!/usr/bin/env bash
set +x
set +e

echo "Environment agent label: ${agentLabel}"
echo "Azure managed identity debug for environment: ${environment}"

COMPUTE_RESPONSE="\$(curl -sS -H Metadata:true 'http://169.254.169.254/metadata/instance/compute?api-version=2021-02-01')"
VM_RESOURCE_ID="\$(printf '%s' "\$COMPUTE_RESPONSE" | sed -n 's/.*"resourceId":"\\([^"]*\\)".*/\\1/p')"
VM_NAME="\$(printf '%s' "\$COMPUTE_RESPONSE" | sed -n 's/.*"name":"\\([^"]*\\)".*/\\1/p')"
VM_RESOURCE_GROUP="\$(printf '%s' "\$COMPUTE_RESPONSE" | sed -n 's/.*"resourceGroupName":"\\([^"]*\\)".*/\\1/p')"

echo "Agent VM name: \$VM_NAME"
echo "Agent VM resource group: \$VM_RESOURCE_GROUP"
echo "Agent VM resource id: \$VM_RESOURCE_ID"

TOKEN_RESPONSE="\$(curl -sS -H Metadata:true 'http://169.254.169.254/metadata/identity/oauth2/token?api-version=2018-02-01&resource=https://management.azure.com/')"
IMDS_CLIENT_ID="\$(printf '%s' "\$TOKEN_RESPONSE" | sed -n 's/.*"client_id":"\\([^"]*\\)".*/\\1/p')"

if [ -n "\$IMDS_CLIENT_ID" ]; then
  echo "IMDS managed identity client_id: \$IMDS_CLIENT_ID"
else
  echo "Unable to read managed identity client_id from IMDS response"
  printf '%s\\n' "\$TOKEN_RESPONSE"
fi

export AZURE_CONFIG_DIR="/opt/jenkins/.azure-mi-debug-${environment}"
az login --identity >/dev/null 2>&1
AZ_LOGIN_EXIT=\$?

if [ "\$AZ_LOGIN_EXIT" -ne 0 ]; then
  echo "az login --identity failed with exit code \$AZ_LOGIN_EXIT"
  exit 0
fi

echo "Azure account after az login --identity:"
az account show --query '{subscription:id, user:user.name, type:user.type, tenant:tenantId}' -o json

AZ_CLIENT_ID="\$(az account show --query user.name -o tsv 2>/dev/null)"
if [ -n "\$AZ_CLIENT_ID" ]; then
  echo "Azure CLI signed-in client_id: \$AZ_CLIENT_ID"
  echo "Matching user-assigned identity in current subscription, if visible:"
  az identity list --query "[?clientId=='\$AZ_CLIENT_ID'].{name:name, resourceGroup:resourceGroup, clientId:clientId, principalId:principalId}" -o table
fi

if [ -n "\$VM_RESOURCE_ID" ]; then
  echo "Managed identities assigned to agent VM, if visible:"
  az vm identity show --ids "\$VM_RESOURCE_ID" -o json
fi

exit 0
"""
  )
}
