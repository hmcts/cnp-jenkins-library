#!/bin/bash
set -x

DEPENDENCY=${1}
REQUIRED_VERSION=${2}

# Attempt to find dependency version in a package.json file
CURRENT_VERSION=""

function ver { printf "%03d%03d%03d%03d" $(echo "$1" | tr '.' ' '); }

# Check if the dependency is a scoped package and add the @ symbol if it is
if [[ $DEPENDENCY == *"/"* ]]; then
    DEPENDENCY="@${DEPENDENCY}"
fi

version=$(yarn info "$DEPENDENCY" version --json 2>/dev/null | jq -r '.data')
if [[ -n "$version" && $version != "null" ]]; then
    CURRENT_VERSION="$version"
    echo "Current version: $CURRENT_VERSION"
    # Only exit with 1 if there is a deprecation spotted
    if [ $(ver $CURRENT_VERSION) -lt $(ver ${REQUIRED_VERSION}) ]; then
        echo "${DEPENDENCY} version ${CURRENT_VERSION} is deprecated... Please upgrade to ${REQUIRED_VERSION}"
        exit 1
    fi
fi
