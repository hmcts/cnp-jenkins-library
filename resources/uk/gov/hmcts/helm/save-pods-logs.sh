#!/bin/bash

RELEASE_NAME=${1}
NAMESPACE=${2}
PODS_LOGS_DIR=${3}

mkdir ${PODS_LOGS_DIR}

echo "Saving pods logs for release ${RELEASE_NAME} under Jenkins artifacts directory ${PODS_LOGS_DIR}..."

for podName in $(kubectl get pods -n "${NAMESPACE}" -l app.kubernetes.io/instance="${RELEASE_NAME}" -o json | jq -r '.items[] | select(.status.containerStatuses[] | ((.ready|not) and .state.waiting.reason=="CrashLoopBackOff")) |  .metadata.name'); do

  kubectl logs -n "${NAMESPACE}" ${podName} > ${PODS_LOGS_DIR}/${podName}.log

done

echo "... done."
