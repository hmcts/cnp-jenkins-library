#!/bin/bash
set -x

DEPENDENCY=${1}
REQUIRED_VERSION=${2}

CURRENT_VERSION=$(./gradlew --no-daemon --init-script init.gradle -q dependencyInsight --no-daemon --dependency ${DEPENDENCY} | grep "${DEPENDENCY}" | head -1 |  sed 's/ (selected by rule)//' | tail -c -6)

function ver { printf "%03d%03d%03d%03d" $(echo "$1" | tr '.' ' '); }

if [[ -n $CURRENT_VERSION ]] && [ $(ver $CURRENT_VERSION) -lt $(ver ${REQUIRED_VERSION}) ]; then
    echo "${DEPENDENCY} version ${CURRENT_VERSION} is deprecated... Please upgrade to ${REQUIRED_VERSION}"
    exit 1
fi
