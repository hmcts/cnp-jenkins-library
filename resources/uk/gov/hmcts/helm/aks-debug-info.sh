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
kubectl get pods -n "${NAMESPACE}"  -l app.kubernetes.io/instance="${RELEASE_NAME}"

# Commenting describe output as that is already covered by events on the namespace
# echo "
#================================================================================
#Describe output of Pending pods:
#"

#kubectl get pods -n "${NAMESPACE}" -l app.kubernetes.io/instance="${RELEASE_NAME}" --field-selector status.phase=Pending -o json | jq '.items[] |  .metadata.name' | xargs kubectl describe pods -n "${NAMESPACE}"

echo "
================================================================================
Output of CrashLoopBackOff pod logs (if any):

"

kubectl get pods -n "${NAMESPACE}" -l app.kubernetes.io/instance="${RELEASE_NAME}"  -o json | jq '.items[] | select(.status.containerStatuses[] | ((.ready|not) and .state.waiting.reason=="CrashLoopBackOff")) |  .metadata.name'| xargs  kubectl logs  -n "${NAMESPACE}" --tail=1000 -p

exit 1
