#!/bin/bash

MODULE_NAME="${1}"      # Module repository name, e.g. terraform-module-postgresql-flexible
DEPRECATED_REF="${2}"   # Deprecated branch reference, e.g. DTSPO-30107-additional-postgres-admins
NEW_REF="${3}"          # Reference teams should move back to, e.g. master
DEADLINE="${4}"         # Date the pipeline will start failing

# Only the repository's own terraform is in scope. .terraform holds modules downloaded by
# terraform init, whose transitive pins teams cannot change from their own repository.
matches=$(grep -r --include='*.tf' --exclude-dir='.terraform' --exclude-dir='.git' \
  -E "${MODULE_NAME}(\.git)?(//[A-Za-z0-9_./-]+)?\?ref=${DEPRECATED_REF}([^A-Za-z0-9._-]|$)" . 2>/dev/null || true)

if [ -z "${matches}" ]; then
  echo "No ${MODULE_NAME} references pinned to the ${DEPRECATED_REF} branch, this is good"
  exit 0
fi

echo "====================================================================================================="
echo "=== You appear to be pinning the ${MODULE_NAME} terraform module to the ${DEPRECATED_REF} branch ==="
echo "=== The changes on this branch are being merged back to ${NEW_REF} ==="
echo "=== Please update the module source to use ?ref=${NEW_REF} in the following files: ==="
echo "${matches}" | cut -d: -f1 | sort -u | sed 's/^/===   /'
echo "=== This pipeline will start failing from ${DEADLINE} ==="
echo "====================================================================================================="
exit 1
