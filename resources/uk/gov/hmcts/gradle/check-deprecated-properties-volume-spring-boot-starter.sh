#!/bin/bash
set -x

function ver { printf "%03d%03d%03d%03d" $(echo "$1" | tr '.' ' '); }

DEPENDENCY="properties-volume-spring-boot-starter"
DEPRECATED_IN="org.springframework.boot:spring-boot"
DEPRECATED_IN_VERSION="2.4.0"
DEPRECATED_LINK="https://github.com/hmcts/properties-volume-spring-boot-starter?tab=readme-ov-file#hmcts-properties-volume-library-deprecated"

DEPENDENCY_IN_USE=$(./gradlew --no-daemon --init-script init.gradle -q dependencyInsight --no-daemon --dependency ${DEPENDENCY} | grep "${DEPENDENCY}" | head -1 | sed 's/ (selected by rule)//')

if [[ -n $DEPENDENCY_IN_USE ]]; then
	CURRENT_VERSION=$(./gradlew --no-daemon --init-script init.gradle -q dependencyInsight --no-daemon --dependency ${DEPRECATED_IN} | grep "${DEPRECATED_IN}" | head -1 | sed 's/ (selected by rule)//' | awk -F':' '{print $NF}')

	if [[ -n $CURRENT_VERSION ]] && [ $(ver $CURRENT_VERSION) -gt $(ver ${DEPRECATED_IN_VERSION}) ]; then
			echo "The use of ${DEPENDENCY} was deprecated in ${DEPRECATED_IN#*:} version ${DEPRECATED_IN_VERSION}... Further info can be found at ${DEPRECATED_LINK}"
			exit 1
	fi
fi
