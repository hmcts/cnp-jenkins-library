#!/usr/bin/env bash

set +e

workspace=$1
s2s_url=$2
s2s_service=$3
camunda_url=$4
product=$5
tenant_id=${6:-null}
filepath="$(realpath "$workspace")/src/main/resources"

for file in $(find "${filepath}" -type f \( -iname "*.bpmn" -o -iname "*.dmn" \))
do 
  # Generate one time password
  otp=$(docker run --rm toolbelt/oathtool -b --totp "${S2S_SECRET}")

  # Retrieve s2s service token
  leaseServiceToken=$(curl --silent --show-error -X POST \
    "${s2s_url}"/lease \
    -H "Accept: text/plain" \
    -H "Content-Type: application/json" \
    -d '{
      "microservice":"'"${s2s_service}"'",
      "oneTimePassword":"'"${otp}"'"
    }'
  )

  # Publish file to Camunda
  uploadResponse=$(curl -v --silent -w "\n%{http_code}" --show-error -X POST \
    "${camunda_url}"/engine-rest/deployment/create \
    -H "Accept: application/json" \
    -H "ServiceAuthorization: Bearer {$leaseServiceToken}" \
    -F "deployment-name=$(basename "${file}")" \
    -F "deploy-changed-only=true" \
    -F "deployment-source=$product" \
    -F "tenant-id=$tenant_id" \
    -F "file=@${filepath}/$(basename "${file}")")

  upload_http_code=$(echo "$uploadResponse" | tail -n1)
  upload_response_content=$(echo "$uploadResponse" | sed '$d')

  if [[ "${upload_http_code}" == '200' ]]; then
    echo "$(basename "${file}") diagram uploaded successfully (${upload_response_content})"
  else
    echo "$(basename "${file}") upload failed with http code ${upload_http_code} and response (${upload_response_content})"
    exit 1;
  fi

done
