#!/bin/bash

CHART_DIRECTORY=${1}-${2}
cd charts/"${CHART_DIRECTORY}" || exit 10

grep --quiet "aadIdentityName" values.preview.template.yaml

if [ $? -gt 0 ]; then
  echo "AAD Identity name not found, this is good"
else
  echo "====================================================================================================="
  echo "=====  Please remove aadIdentityName from values.preview.template.yaml                         ======"
  echo "=====  This is required as we are going to start enforcing                                     ======"
  echo "=====  authentication with your pod's identity like all other AKS environments and not kvcreds ======"
  echo "====================================================================================================="
  exit 1
fi
