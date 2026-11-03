#!/bin/bash
set -e

PINNED_VERSION_RECOMMENDATION="Check for the latest release at https://github.com/hmcts/cnp-jenkins-library/releases, then update your Jenkinsfile to use a fixed library version."
WARNING_BANNER_FILE="warning-banner.txt"

warning_banner () {
    cat "${WARNING_BANNER_FILE}"
}

old_library_found () {
    echo ""
    echo "Old library version references found."
    echo "Update your Jenkinsfile to use: @Library(\"Infrastructure@${NEW_LIBRARY_VERSION}\")"
    echo ""
    echo "Deadline for updating: ${DEADLINE}"
    echo "Once the deadline passes, this library version will no longer be supported and the pipeline will fail."
    echo ""
    exit 1
}

unpinned_library_found () {
    echo ""
    echo "Unpinned Infrastructure library reference found."
    echo "This Jenkinsfile is using @Library(\"Infrastructure\") and is therefore tracking the default branch (master), not a fixed library version."
    echo "This means your pipeline could break unexpectedly when upstream changes are made to the library."
    echo "${PINNED_VERSION_RECOMMENDATION}"
    echo ""
    echo ""
    exit 1
}

no_custom_library_found () {
    echo "No old references found. All clear!"
    exit 0
}

scan_jenkinsfiles () {
    local library_pattern="$1"

    JENKINSFILES=$(find . -maxdepth 1 \( -name "Jenkinsfile" -o -name "Jenkinsfile_CNP" -o -name "Jenkinsfile_nightly" \) -type f -exec grep -l -E "${library_pattern}" {} + 2>/dev/null || true)
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
        warning_banner
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

WARNING_MODE="${1:-deprecation}" # warning mode - can be 'unpinned' or 'deprecation'
CUSTOM_LIBRARY_VERSION="${2:-}"  # Version to detect, can be empty when detecting unpinned libraries
NEW_LIBRARY_VERSION="${3:-}"     # New library version to suggest, can be empty for unpinned warnings
DEADLINE="${4:-}"                # Deadline for updating the library version, can be empty for unpinned warnings
ESCAPED_CUSTOM_LIBRARY_VERSION=$(printf '%s' "${CUSTOM_LIBRARY_VERSION}" | sed 's/[][\\.^$*+?(){}|]/\\&/g')

FOUND_REFERENCES=0
FAILED_FILES=()

if [[ "${WARNING_MODE}" == "unpinned" ]]; then
    echo "Checking for unpinned Infrastructure library references."
else
    echo "Checking for '${WARNING_MODE}' Infrastructure library references: '${CUSTOM_LIBRARY_VERSION}'"
fi

echo "Scanning Jenkinsfile..."

if [[ "${WARNING_MODE}" == "unpinned" ]]; then
    LIBRARY_PATTERN="@Library\\([\"']?Infrastructure[\"']?\\)"
    scan_jenkinsfiles "${LIBRARY_PATTERN}"
    warn_for_found_references "Unpinned Infrastructure library in use!" unpinned_library_found
    no_custom_library_found
fi

LIBRARY_PATTERN="@Library\\([\"']?Infrastructure@${ESCAPED_CUSTOM_LIBRARY_VERSION}[\"']?\\)"
scan_jenkinsfiles "${LIBRARY_PATTERN}"
warn_for_found_references "Deprecated library version in use!" old_library_found

no_custom_library_found
