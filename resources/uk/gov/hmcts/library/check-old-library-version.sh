#!/bin/bash
set -e

old_library_found () {
    echo "Old library version references found. Please update your Jenkinsfile to use the new library version."
    exit 1
}

no_old_library_found () {
    echo "No old library version references found. All clear!"
    exit 0
}

OLD_LIBRARY_VERSION="${1}"  # Pattern of old library version to detect

FOUND_REFERENCES=0
FAILED_FILES=()

echo "Checking for old library version references: ${OLD_LIBRARY_VERSION}"

# Check if this is a nightly pipeline
if [[ "$JOB_NAME" == *"nightly"* ]]; then
    echo "Running nightly pipeline. No need to check for old library version."
    no_old_library_found
fi

# Check if this pipeline is running on sandbox
if [[ "$JENKINS_SUBSCRIPTION_NAME" == *"SBOX"* ]]; then
    echo "Running on Sandbox Jenkins. No need to check for old library version."
    no_old_library_found
fi

# Check Jenkinsfile
echo "Scanning Jenkinsfile..."
JENKINSFILES=$(find . -maxdepth 3 -name "Jenkinsfile_CNP" -type f -exec grep -l -E "${OLD_LIBRARY_VERSION}" {} + 2>/dev/null || true)
if [ -n "$JENKINSFILES" ]; then
    FOUND_REFERENCES=1
    while IFS= read -r file; do
        [ -n "$file" ] && FAILED_FILES+=("$file")
    done <<< "$JENKINSFILES"
fi

if [ $FOUND_REFERENCES -eq 1 ]; then
    echo ""
    echo "╔════════════════════════════════════════════════════════════════╗"
    echo "║  WARNING: Deprecated library version in use!      ║"
    echo "╚════════════════════════════════════════════════════════════════╝"
    echo ""
    echo "Files that need to be updated:"
    for file in "${FAILED_FILES[@]}"; do
        # Only print non-empty file paths
        if [ -n "$file" ]; then
            echo "  ❌ $file"
        fi
    done
    old_library_found
fi

no_old_library_found
