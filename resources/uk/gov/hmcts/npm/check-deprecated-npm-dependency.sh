#!/bin/bash
set -x

function ver { printf "%03d%03d%03d%03d" $(echo "$1" | tr '.' ' '); }

DEPENDENCY=${1}
REQUIRED_VERSION=${2}

# Attempt to find dependency version in a package.json file and trims version to get the major version value 
CURRENT_VERSION=""
version=$(yarn info "$DEPENDENCY" --json | jq -r '.children.Version')
if [[ -n "$version" ]]; then
    CURRENT_VERSION="$version"
    echo "Current version: $CURRENT_VERSION"
    # Only exit with 1 if there is a deprecation spotted
    if [ $(ver $CURRENT_VERSION) -lt $(ver $REQUIRED_VERSION) ]; then
        echo "${DEPENDENCY} version ${CURRENT_VERSION} is deprecated... Please upgrade to ${REQUIRED_VERSION}"
        exit 1
    fi
fi
