#!/bin/bash

CHART_DIRECTORY=${1}-${2}
cd charts/"${CHART_DIRECTORY}" || exit 10

grep --quiet "useWorkloadIdentity: true" values.preview.template.yaml

if [ $? -gt 0 ]; then
  echo "==========================================================================================================="
  echo "=====  Please migrate your application to use workload identity, missing useWorkloadIdentity: true.  ======"
  echo "==========================================================================================================="
  exit 1
else
  echo "Application has workload identity enabled."
fi
