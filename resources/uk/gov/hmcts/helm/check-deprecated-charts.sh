#!/bin/bash
set -x

CHART_DIRECTORY=${1}-${2}
CHART_NAME=${3}
CHART_VERSION=${4}

function ver { printf "%03d%03d%03d%03d" $(echo "$1" | tr '.' ' '); }


CURRENT_VERSION=$(helm dependency ls charts/${CHART_DIRECTORY}/ | grep "^${CHART_NAME}" |awk '{ print $2}' | sed "s/~//g")
if [[ -n $CURRENT_VERSION ]] && [ $(ver $CURRENT_VERSION) -lt $(ver ${CHART_VERSION}) ]; then
    echo "$deprecation chart $CURRENT_VERSION is deprecated, please upgrade to at least ${CHART_VERSION}"
    exit 1
fi
