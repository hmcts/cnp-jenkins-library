#!/bin/bash

CHART_DIRECTORY=${1}-${2}
cd charts/"${CHART_DIRECTORY}" || exit 10
# Check if app still has aad config
grep --quiet  "aadIdentityName:" values.yaml
if [ $? -gt 0 ]; then
  echo "Application has removed aad pod binding references."
else
  echo "=========================================================================================================================="
  echo "========  Please fully migrate your application to use workload identity, app is still using aadIdentityName flag.  ======"
  echo "=========================================================================================================================="
  exit 1
fi
