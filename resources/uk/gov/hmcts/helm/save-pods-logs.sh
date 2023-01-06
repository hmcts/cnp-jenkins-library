#!/bin/bash

RELEASE_NAME=${1}
NAMESPACE=${2}
PODS_LOGS_DIR=${3}

mkdir ${PODS_LOGS_DIR}

echo "
Check Jenkins artifacts for the pods logs.
================================================================================
"

kubectl get pods -n "${NAMESPACE}" -l app.kubernetes.io/instance="${RELEASE_NAME}" | awk '{print $1}'

for podName in $(kubectl get pods -n "${NAMESPACE}" -l app.kubernetes.io/instance="${RELEASE_NAME}" | awk '{print $1}'); do

  kubectl logs -n "${NAMESPACE}" ${podName} > ${PODS_LOGS_DIR}/${podName}.log

done

