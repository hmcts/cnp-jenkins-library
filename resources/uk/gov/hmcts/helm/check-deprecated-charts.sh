#!/bin/bash
set -x

CHART_DIRECTORY=${1}-${2}
DEPRECATED_CHART_NAME=${3}
DEPRECATED_CHART_VERSION=${4}

function ver { printf "%03d%03d%03d%03d" $(echo "$1" | tr '.' ' '); }


CURRENT_VERSION=$(helm dependency ls charts/${CHART_DIRECTORY}/ | grep "^${DEPRECATED_CHART_NAME}" |awk '{ print $2}' | sed "s/~//g")
# Only run deprecation checks on non beta/alpha chart versions
if [[ ! $CURRENT_VERSION == *"beta"* ]] && [[ ! $CURRENT_VERSION == *"alpha"* ]]; then
    if [[ -n $CURRENT_VERSION ]] && [ $(ver $CURRENT_VERSION) -lt $(ver ${DEPRECATED_CHART_VERSION}) ]; then
        echo "$deprecation chart $CURRENT_VERSION is deprecated, please upgrade to at least ${DEPRECATED_CHART_VERSION}"
        exit 1
    fi
fi
