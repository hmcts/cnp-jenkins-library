#!/bin/bash
set -x

DEPENDENCY=${1}
REQUIRED_VERSION=${2}

# Attempt to find dependency version in a package.json file
CURRENT_VERSION=""

version=$(yarn info "$DEPENDENCY" --json | jq -r '.children.Version' | cut -d '.' -f 1)
if [[ -n "$version" ]]; then
    CURRENT_VERSION="$version"
    echo "Current version: $CURRENT_VERSION"
    # Only exit with 1 if there is a deprecation spotted
    if [ ${CURRENT_VERSION} -lt ${REQUIRED_VERSION} ]; then
        echo "${DEPENDENCY} version ${CURRENT_VERSION} is deprecated... Please upgrade to ${REQUIRED_VERSION}"
        exit 1
    fi
fi
