#!/bin/bash
set -x

DEPENDENCY=${1}
REQUIRED_VERSION=${2}

# Attempt to find angular version in a package.json file and trims version of angular/core to get the major version value 
CURRENT_VERSION=""
for file in $(find . -type f -name "package.json" -not -path "./node_modules/*"); do
    version=$(cat "$file" |  grep "$DEPENDENCY" | sed 's/.*"\(.*\)".*/\1/' | tr -d '^' | cut -d '.' -f 1 )
    if [[ -n "$version" ]]; then
        CURRENT_VERSION="$version"
        echo "Current version: $CURRENT_VERSION"
        break
    fi
done

# Only exit with 1 if there is a deprecation spotted
if [[ -n $CURRENT_VERSION ]] && [ ${CURRENT_VERSION} -lt ${REQUIRED_VERSION} ]; then
    echo "${DEPENDENCY} version ${CURRENT_VERSION} is deprecated... Please upgrade to ${REQUIRED_VERSION}"
    exit 1
fi