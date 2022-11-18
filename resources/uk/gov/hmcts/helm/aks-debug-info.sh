#!/bin/bash

RELEASE_NAME=${1}
NAMESPACE=${2}
echo "
See below debug information to help troubleshooting the issue.
================================================================================
Events on namespace:
"
kubectl get events '--field-selector=type!=Normal' '--sort-by=.metadata.creationTimestamp' -n "${NAMESPACE}" | grep  "${RELEASE_NAME}"

echo "
================================================================================
Status of pods:

"
kubectl get pods -n "${NAMESPACE}"  | grep  "${RELEASE_NAME}"
exit 1
