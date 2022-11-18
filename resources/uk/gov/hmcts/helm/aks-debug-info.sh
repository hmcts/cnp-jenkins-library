#!/bin/bash

RELEASE_NAME=${1}
NAMESPACE=${2}
echo "Helm release has failed, below are the events from cluster:"
kubectl get events '--field-selector=type!=Normal' '--sort-by=.metadata.creationTimestamp' -n "${NAMESPACE}" | grep  "${RELEASE_NAME}"
echo "Status of pods:"
kubectl get pods -n "${NAMESPACE}"  | grep  "${RELEASE_NAME}"
exit 1
