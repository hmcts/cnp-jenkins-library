#!/bin/bash
set -x

DEPENDENCY=${1}
REQUIRED_VERSION=${2}

# Fetches first package.json file and trims version of angular/core to get the major version value 
find . -type f -name "package.json" | head -1 | xargs cat
find . -type f -name "package.json" | head -1 | xargs cat | grep $DEPENDENCY 
CURRENT_VERSION=$(find . -type f -name "package.json" | head -1 | xargs cat | grep $DEPENDENCY | sed 's/.*"\^\(.*\)".*/\1/' | cut -d '.' -f 1)

# Only exit with 1 if there is a deprecation spotted
if [[ -n $CURRENT_VERSION ]] && [ ${CURRENT_VERSION} -lt ${REQUIRED_VERSION} ]; then
    echo "${DEPENDENCY} version ${CURRENT_VERSION} is deprecated... Please upgrade to ${REQUIRED_VERSION}"
    exit 1
fi