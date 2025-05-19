#!/bin/bash
# set -x

DEPENDENCY=${1}
REQUIRED_VERSION=${2}

# Attempt to find dependency version in a package.json file
CURRENT_VERSION=""

function ver { printf "%03d%03d%03d%03d" $(echo "$1" | tr '.' ' '); }

# Check if the dependency is a scoped package and add the @ symbol if it is
if [[ $DEPENDENCY == *"/"* ]]; then
    DEPENDENCY="@${DEPENDENCY}"
fi
versionOutput=$(yarn info ${DEPENDENCY} version --json 2>/dev/null)

if echo "$versionOutput" | jq -e .children.Version >/dev/null 2>&1; then
    version=$(yarn info "$DEPENDENCY" --json | jq -r '.children.Version')
    if [[ -n "$version" && $version != "null" ]]; then
        echo "Current version: $version"
        if [ $(ver $version) -lt $(ver ${REQUIRED_VERSION}) ]; then
            echo "${DEPENDENCY} version ${version} is deprecated... Please upgrade to ${REQUIRED_VERSION}"
            exit 1
    fi
fi
else
    echo "Failed to find ${DEPENDENCY} use in project."
fi