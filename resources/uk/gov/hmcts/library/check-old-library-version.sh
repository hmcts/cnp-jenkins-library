#!/bin/bash
set -e

OLD_LIBRARY_VERSION="${1}"  # Pattern of old library version to detect

FOUND_REFERENCES=0
FAILED_FILES=()

echo "Checking for old library version references: ${OLD_LIBRARY_VERSION}"

# Check Jenkinsfile
echo "Scanning Jenkinsfile..."
JENKINSFILES=$(find . -maxdepth 3 -name "Jenkinsfile_*" -type f -exec grep -l -E "${OLD_LIBRARY_VERSION}" {} + 2>/dev/null || true)
if [ -n "$JENKINSFILES" ]; then
    echo "$JENKINSFILES"
    echo ""
    echo "Found old library version references in Jenkinsfile(s) above"
    FOUND_REFERENCES=1
    while IFS= read -r file; do
        [ -n "$file" ] && FAILED_FILES+=("$file")
    done <<< "$JENKINSFILES"
fi

# Check Dockerfiles (including variants like Dockerfile.build, Dockerfile.dev, etc.)
echo "Scanning Dockerfiles..."
DOCKER_FILES=$(find . -name "Dockerfile*" -type f -exec grep -l -E "${OLD_LIBRARY_VERSION}" {} + 2>/dev/null || true)
if [ -n "$DOCKER_FILES" ]; then
    echo "$DOCKER_FILES"
    echo ""
    echo "Found old library version references in Dockerfile(s) above"
    FOUND_REFERENCES=1
    while IFS= read -r file; do
        [ -n "$file" ] && FAILED_FILES+=("$file")
    done <<< "$DOCKER_FILES"
fi

# Check Helm chart files
echo "Scanning Helm charts..."
if [ -d "charts" ]; then
    # Check Chart.yaml files
    CHART_FILES=$(find charts -name "Chart.yaml" -type f -exec grep -l -E "${OLD_LIBRARY_VERSION}" {} + 2>/dev/null || true)
    if [ -n "$CHART_FILES" ]; then
        echo "$CHART_FILES"
        echo ""
        echo "Found old ACR references in Chart.yaml file(s) above"
        FOUND_REFERENCES=1
        while IFS= read -r file; do
            [ -n "$file" ] && FAILED_FILES+=("$file")
        done <<< "$CHART_FILES"
    fi

    # Check values files (values.yaml, values-*.yaml, etc.)
    VALUES_FILES=$(find charts -name "values*.yaml" -type f -exec grep -l -E "${OLD_LIBRARY_VERSION}" {} + 2>/dev/null || true)
    if [ -n "$VALUES_FILES" ]; then
        echo "$VALUES_FILES"
        echo ""
        echo "Found old ACR references in values file(s) above"
        FOUND_REFERENCES=1
        while IFS= read -r file; do
            [ -n "$file" ] && FAILED_FILES+=("$file")
        done <<< "$VALUES_FILES"
    fi

    # Check template files
    TEMPLATE_FILES=$(find charts -path "*/templates/*" -name "*.yaml" -type f -exec grep -l -E "${OLD_LIBRARY_VERSION}" {} + 2>/dev/null || true)
    if [ -n "$TEMPLATE_FILES" ]; then
        echo "$TEMPLATE_FILES"
        echo ""
        echo "Found old ACR references in Helm template(s) above"
        FOUND_REFERENCES=1
        while IFS= read -r file; do
            [ -n "$file" ] && FAILED_FILES+=("$file")
        done <<< "$TEMPLATE_FILES"
    fi
fi

# Check additional values files in root or other common locations
OTHER_VALUES=$(find . -maxdepth 3 -name "values*.yaml" -type f ! -path "*/charts/*" -exec grep -l -E "${OLD_LIBRARY_VERSION}" {} + 2>/dev/null || true)
if [ -n "$OTHER_VALUES" ]; then
    echo "$OTHER_VALUES"
    echo ""
    echo "Found old ACR references in values file(s) above"
    FOUND_REFERENCES=1
    while IFS= read -r file; do
        [ -n "$file" ] && FAILED_FILES+=("$file")
    done <<< "$OTHER_VALUES"
fi

# Check kubernetes/helm directories if they exist outside of standard charts location
for dir in kubernetes k8s helm; do
    if [ -d "$dir" ]; then
        K8S_FILES=$(find "$dir" -name "*.yaml" -type f -exec grep -l -E "${OLD_LIBRARY_VERSION}" {} + 2>/dev/null || true)
        if [ -n "$K8S_FILES" ]; then
            echo "$K8S_FILES"
            echo ""
            echo "Found old ACR references in $dir directory above"
            FOUND_REFERENCES=1
            while IFS= read -r file; do
                [ -n "$file" ] && FAILED_FILES+=("$file")
            done <<< "$K8S_FILES"
        fi
    fi
done

if [ $FOUND_REFERENCES -eq 1 ]; then
    echo ""
    echo "╔════════════════════════════════════════════════════════════════╗"
    echo "║  ERROR: Old Azure Container Registry references detected!      ║"
    echo "╚════════════════════════════════════════════════════════════════╝"
    echo ""
    echo "Files that need to be updated:"
    for file in "${FAILED_FILES[@]}"; do
        # Only print non-empty file paths
        if [ -n "$file" ]; then
            echo "  ❌ $file"
        fi
    done
    echo ""
    echo "Please update these files to use the new registries:"
    echo "  • hmctsprod.azurecr.io (for production images)"
    echo "  • hmctssbox.azurecr.io (for sandbox/non-prod images)"
    echo ""
    exit 1
fi

echo "No old ACR references found. All clear!"
exit 0
