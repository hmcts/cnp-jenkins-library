#!/bin/bash
set -e

old_library_found () {
    echo ""
    echo "Old library version references found."
    echo "Update your Jenkinsfile to use: @Library(\"Infrastructure@${NEW_LIBRARY_VERSION}\")"
    echo ""
    echo "Before raising a PR, check the migration guide and rollout tracker."
    echo "Some repositories also need Key Vault or PostgreSQL module changes as part of this migration."
    echo ""
    echo "Migration guide: https://tools.hmcts.net/confluence/spaces/DTSPO/pages/1973509936/Jenkins+Library+Migration+Guide"
    echo "Rollout tracker: https://tools.hmcts.net/confluence/spaces/DTSPO/pages/1973305638/Migration+rollout+tracker"
    echo ""
    echo "Deadline for updating: ${DEADLINE}"
    echo ""
    exit 1
}

no_old_library_found () {
    echo "No old library version references found. All clear!"
    exit 0
}

OLD_LIBRARY_VERSION="${1}"  # Pattern of old library version to detect
NEW_LIBRARY_VERSION="${2}"  # New library version to suggest in the warning message
DEADLINE="${3}"             # Deadline for updating the library version

FOUND_REFERENCES=0
FAILED_FILES=()

echo "Checking for old library version references: ${OLD_LIBRARY_VERSION}"

if [[ "$JOB_NAME" == *"nightly"* ]]; then
    echo "Running nightly pipeline. No need to check for old library version."
    no_old_library_found
fi

if [[ "$JENKINS_SUBSCRIPTION_NAME" == *"SBOX"* ]]; then
    echo "Running on Sandbox Jenkins. No need to check for old library version."
    no_old_library_found
fi

echo "Scanning Jenkinsfile..."
LIBRARY_PATTERN='@Library\("?Infrastructure(@'"${OLD_LIBRARY_VERSION}"')?"?\)'
JENKINSFILES=$(find . -maxdepth 1 \( -name "Jenkinsfile" -o -name "Jenkinsfile_CNP" \) -type f -exec grep -l -E "${LIBRARY_PATTERN}" {} + 2>/dev/null || true)
if [ -n "$JENKINSFILES" ]; then
    FOUND_REFERENCES=1
    while IFS= read -r file; do
        [ -n "$file" ] && FAILED_FILES+=("$file")
    done <<< "$JENKINSFILES"
fi

if [ $FOUND_REFERENCES -eq 1 ]; then
    echo ""
    echo "WARNING: Deprecated library version in use!"
    echo ""
    echo "Files that need to be updated:"
    for file in "${FAILED_FILES[@]}"; do
        if [ -n "$file" ]; then
            echo "  - $file"
        fi
    done
    old_library_found
fi

no_old_library_found
