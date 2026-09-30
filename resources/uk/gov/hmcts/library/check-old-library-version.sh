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
    echo "Migration guide: https://hmcts.atlassian.net/wiki/spaces/DTSPO/pages/277283079/Jenkins+Library+Migration+Guide"
    echo "Rollout tracker: https://hmcts.atlassian.net/wiki/spaces/DTSPO/pages/277283023/Migration+rollout+tracker"
    echo ""
    echo "Deadline for updating: ${DEADLINE}"
    echo ""
    exit 1
}

unpinned_library_found () {
    echo ""
    echo "Unpinned Infrastructure library reference found."
    echo "This Jenkinsfile is using @Library(\"Infrastructure\") and is therefore tracking the default branch (master), not a fixed library version."
    echo "This means your pipeline could break unexpectedly when upstream changes are made to the library."
    echo "Update your Jenkinsfile to use: @Library(\"Infrastructure@${NEW_LIBRARY_VERSION}\")"
    echo ""
    echo ""
    exit 1
}

no_old_library_found () {
    echo "No old library version references found. All clear!"
    exit 0
}

scan_jenkinsfiles () {
    local library_pattern="$1"

    JENKINSFILES=$(find . -maxdepth 1 \( -name "Jenkinsfile" -o -name "Jenkinsfile_CNP" \) -type f -exec grep -l -E "${library_pattern}" {} + 2>/dev/null || true)
    if [ -n "$JENKINSFILES" ]; then
        FOUND_REFERENCES=1
        while IFS= read -r file; do
            [ -n "$file" ] && FAILED_FILES+=("$file")
        done <<< "$JENKINSFILES"
    fi
}

warn_for_found_references () {
    local warning="$1"
    local warning_handler="$2"

    if [ $FOUND_REFERENCES -eq 1 ]; then
        echo ""
        echo "WARNING: ${warning}"
        echo ""
        echo "Files that need to be updated:"
        for file in "${FAILED_FILES[@]}"; do
            if [ -n "$file" ]; then
                echo "  - $file"
            fi
        done
        "$warning_handler"
    fi
}

OLD_LIBRARY_VERSION="${1}"  # Pattern of old library version to detect
NEW_LIBRARY_VERSION="${2}"  # New library version to suggest in the warning message
DEADLINE="${3}"             # Deadline for updating the library version
WARNING_MODE="${4:-deprecation}"

FOUND_REFERENCES=0
FAILED_FILES=()

echo "Checking for old library version references: ${OLD_LIBRARY_VERSION}"

if [[ "${JOB_NAME,,}" == *"nightly"* ]]; then
    echo "Running nightly pipeline. No need to check for old library version."
    no_old_library_found
fi

if [[ "$JENKINS_SUBSCRIPTION_NAME" == *"SBOX"* ]]; then
    echo "Running on Sandbox Jenkins. No need to check for old library version."
    no_old_library_found
fi

echo "Scanning Jenkinsfile..."
if [[ "${WARNING_MODE}" == "unpinned" ]]; then
    LIBRARY_PATTERN='@Library\("?Infrastructure"?\)'
    scan_jenkinsfiles "${LIBRARY_PATTERN}"
    warn_for_found_references "Unpinned Infrastructure library in use!" unpinned_library_found
    no_old_library_found
fi

# versions will have dots in them so escape those so they are not accidentally poisoning regex
ESCAPED_OLD_LIBRARY_VERSION="${OLD_LIBRARY_VERSION//./\\.}"
LIBRARY_PATTERN='@Library\("?Infrastructure@'"${ESCAPED_OLD_LIBRARY_VERSION}"'"?\)'
scan_jenkinsfiles "${LIBRARY_PATTERN}"
warn_for_found_references "Deprecated library version in use!" old_library_found

no_old_library_found
