#!/bin/bash
set -x

CHART_DIRECTORY=${1}-${2}
DEPRECATED_CHART_NAME=${3}
DEPRECATED_CHART_VERSION=${4}

function ver { 
    # Clean the version: remove v prefix
    local clean_version=$(echo "$1" | sed 's/^v//')
    printf "%03d%03d%03d%03d" $(echo "$clean_version" | tr '.' ' '); 
}


CURRENT_VERSION=$(helm dependency ls charts/${CHART_DIRECTORY}/ | grep "^${DEPRECATED_CHART_NAME}" | awk '{ print $2}' | sed "s/~//g" | grep -v -E '\-(alpha|beta)' | head -1)
if [[ -n $CURRENT_VERSION ]] && [ $(ver $CURRENT_VERSION) -lt $(ver ${DEPRECATED_CHART_VERSION}) ]; then
    echo "$deprecation chart $CURRENT_VERSION is deprecated, please upgrade to at least ${DEPRECATED_CHART_VERSION}"
    exit 1
fi
