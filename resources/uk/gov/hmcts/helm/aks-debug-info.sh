#!/bin/bash
RELEASE_NAME=${1}
NAMESPACE=${2}
export TERM=xterm-256color

echo "

See below debug information to help troubleshooting the issue.
================================================================================
kubectl get events '--field-selector=type!=Normal' '--sort-by=.metadata.creationTimestamp' -n "${NAMESPACE}" | grep  "${RELEASE_NAME}"
"
tput setaf 3
kubectl get events '--field-selector=type!=Normal' '--sort-by=.metadata.creationTimestamp' -n "${NAMESPACE}" | grep  "${RELEASE_NAME}"
tput sgr0
echo "
================================================================================
kubectl get pods -n "${NAMESPACE}"  -l app.kubernetes.io/instance="${RELEASE_NAME}"

"
tput setaf 3
kubectl get pods -n "${NAMESPACE}"  -l app.kubernetes.io/instance="${RELEASE_NAME}"
tput sgr0
# Commenting describe output as that is already covered by events on the namespace
# echo "
#================================================================================
#Describe output of Pending pods:
#"

#kubectl get pods -n "${NAMESPACE}" -l app.kubernetes.io/instance="${RELEASE_NAME}" --field-selector status.phase=Pending -o json | jq '.items[] |  .metadata.name' | xargs kubectl describe pods -n "${NAMESPACE}"

for podName in $(kubectl get pods -n "${NAMESPACE}" -l app.kubernetes.io/instance="${RELEASE_NAME}" -o json | jq  -r '.items[] | select(.status.containerStatuses[] | ((.ready|not) and .state.waiting.reason=="CrashLoopBackOff")) |  .metadata.name'); do

echo "
================================================================================
Logs for crashing pod $podName:
kubectl logs  -n "${NAMESPACE}" ${podName} -p
"

tput setaf 3
kubectl logs  -n "${NAMESPACE}" ${podName} -p
tput sgr0

done

exit 1
