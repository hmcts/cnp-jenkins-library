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
    if [ -n "${NEW_LIBRARY_VERSION}" ]; then
        echo "Update your Jenkinsfile to use: @Library(\"Infrastructure@${NEW_LIBRARY_VERSION}\")"
    else
        echo "${PINNED_VERSION_RECOMMENDATION}"
    fi
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

is_semantic_version () {
    [[ "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]
}

version_is_at_or_below () {
    local current_version="$1"
    local deprecated_version="$2"
    local current_major current_minor current_patch
    local deprecated_major deprecated_minor deprecated_patch

    IFS='.' read -r current_major current_minor current_patch <<< "${current_version}"
    IFS='.' read -r deprecated_major deprecated_minor deprecated_patch <<< "${deprecated_version}"

    (( 10#${current_major} < 10#${deprecated_major} )) && return 0
    (( 10#${current_major} > 10#${deprecated_major} )) && return 1
    (( 10#${current_minor} < 10#${deprecated_minor} )) && return 0
    (( 10#${current_minor} > 10#${deprecated_minor} )) && return 1
    (( 10#${current_patch} <= 10#${deprecated_patch} ))
}

scan_jenkinsfiles_for_deprecated_versions () {
    local deprecated_version="$1"
    local file current_version

    while IFS= read -r file; do
        [ -n "${file}" ] || continue

        while IFS= read -r current_version; do
            [ -n "${current_version}" ] || continue
            if version_is_at_or_below "${current_version}" "${deprecated_version}"; then
                FOUND_REFERENCES=1
                FAILED_FILES+=("${file}")
                break
            fi
        done < <(
            grep -Eo "@Library\\([[:space:]]*[\"']?Infrastructure@[0-9]+\\.[0-9]+\\.[0-9]+[\"']?[[:space:]]*\\)" "${file}" 2>/dev/null |
                sed -E 's/.*Infrastructure@([0-9]+\.[0-9]+\.[0-9]+).*/\1/' || true
        )
    done < <(find . -maxdepth 1 \( -name "Jenkinsfile" -o -name "Jenkinsfile_CNP" -o -name "Jenkinsfile_nightly" \) -type f)
}

warn_for_found_references () {
    local warning="$1"
    local warning_handler="$2"

    if [ $FOUND_REFERENCES -eq 1 ]; then
        echo ""
        warning_banner
        echo "WARNING: ${warning}"
        echo ""
        if [ ${#FAILED_FILES[@]} -gt 0 ]; then
            echo "Files that need to be updated:"
            for file in "${FAILED_FILES[@]}"; do
                if [ -n "$file" ]; then
                    echo "  - $file"
                fi
            done
        fi
        if [ -n "${DEPRECATED_RUNTIME_VERSION}" ]; then
            echo "Loaded Infrastructure library version: ${DEPRECATED_RUNTIME_VERSION}"
        fi
        "$warning_handler"
    fi
}

WARNING_MODE="${1:-deprecation}" # warning mode - can be 'unpinned' or 'deprecation'
CUSTOM_LIBRARY_VERSION="${2:-}"  # Version to detect, can be empty when detecting unpinned libraries
NEW_LIBRARY_VERSION="${3:-}"     # Optional version to suggest; when empty, recommend the latest release
DEADLINE="${4:-}"                # Deadline for updating the library version, can be empty for unpinned warnings
CURRENT_LIBRARY_VERSION="${5:-}" # Version loaded by Jenkins, when runtime metadata is available
ESCAPED_CUSTOM_LIBRARY_VERSION=$(printf '%s' "${CUSTOM_LIBRARY_VERSION}" | sed 's/[][\\.^$*+?(){}|]/\\&/g')

FOUND_REFERENCES=0
FAILED_FILES=()
DEPRECATED_RUNTIME_VERSION=""

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

if is_semantic_version "${CUSTOM_LIBRARY_VERSION}"; then
    if is_semantic_version "${CURRENT_LIBRARY_VERSION}" && version_is_at_or_below "${CURRENT_LIBRARY_VERSION}" "${CUSTOM_LIBRARY_VERSION}"; then
        FOUND_REFERENCES=1
        DEPRECATED_RUNTIME_VERSION="${CURRENT_LIBRARY_VERSION}"
    fi
    scan_jenkinsfiles_for_deprecated_versions "${CUSTOM_LIBRARY_VERSION}"
else
    LIBRARY_PATTERN="@Library\\([\"']?Infrastructure@${ESCAPED_CUSTOM_LIBRARY_VERSION}[\"']?\\)"
    scan_jenkinsfiles "${LIBRARY_PATTERN}"
fi
warn_for_found_references "Deprecated library version in use!" old_library_found

no_custom_library_found
