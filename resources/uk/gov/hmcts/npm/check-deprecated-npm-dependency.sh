#!/bin/bash
set -x

DEPENDENCY=${1}
REQUIRED_VERSION=${2}

# Attempt to find angular version in a package.json file and trims version of angular/core to get the major version value 
CURRENT_VERSION=""
version=$(yarn info "@angular/core" --json | jq -r '.children.Version' | cut -d '.' -f 1 )
if [[ -n "$version" ]]; then
    CURRENT_VERSION="$version"
    echo "Current version: $CURRENT_VERSION"
    # Only exit with 1 if there is a deprecation spotted
    if [ ${CURRENT_VERSION} -lt ${REQUIRED_VERSION} ]; then
        echo "${DEPENDENCY} version ${CURRENT_VERSION} is deprecated... Please upgrade to ${REQUIRED_VERSION}"
        exit 1
    fi
fi
