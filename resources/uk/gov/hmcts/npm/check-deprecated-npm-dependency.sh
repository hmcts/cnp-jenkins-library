#!/bin/bash
set -x

function ver { printf "%03d%03d%03d%03d" $(echo "$1" | tr '.' ' '); }

DEPENDENCY=${1}
REQUIRED_VERSION=${2}

# Attempt to find dependency version in a package.json file
CURRENT_VERSION=""

# Handle scoped packages
if [[ "$DEPENDENCY" == *"/"* ]]; then
    DEPENDENCY="@$DEPENDENCY"
fi

version=$(yarn info "$DEPENDENCY" --json | jq -r '.children.Version')
if [[ $? -eq 0 && -n "$version" ]]; then
    CURRENT_VERSION=$version
    echo "Current version: $CURRENT_VERSION"
    # Only exit with 1 if there is a deprecation spotted
    if [ $(ver $CURRENT_VERSION) -lt $(ver $REQUIRED_VERSION) ]; then
        echo "${DEPENDENCY} version ${CURRENT_VERSION} is deprecated... Please upgrade to ${REQUIRED_VERSION}"
        exit 1
    fi
fi
