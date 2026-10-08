#!/bin/bash
set -euo pipefail

MINIMUM_SUPPORTED_VERSION="${1:?Minimum supported PostgreSQL version is required}"
PLAN_FILE="${2:?Terraform plan file is required}"

if [[ ! "$MINIMUM_SUPPORTED_VERSION" =~ ^[0-9]+$ ]]; then
  echo "Invalid minimum supported PostgreSQL version: $MINIMUM_SUPPORTED_VERSION" >&2
  exit 2
fi

postgres_resources=$(terraform show -json "$PLAN_FILE" | jq -r '
  .planned_values.root_module
  | [.. | objects
    | select(.mode? == "managed"
      and (.type? == "azurerm_postgresql_flexible_server"
        or .type? == "azurerm_postgresql_server"))
    | [(.address // "unknown"), ((.values.version // "unknown") | tostring)]
    | @tsv]
  | .[]')

while IFS=$'\t' read -r address version; do
  [[ -n "$address" ]] || continue

  major_version="${version%%.*}"
  if [[ ! "$major_version" =~ ^[0-9]+$ ]]; then
    echo "Unable to determine PostgreSQL version for Terraform resource $address: $version" >&2
    exit 2
  fi

  if (( 10#$major_version < 10#$MINIMUM_SUPPORTED_VERSION )); then
    printf '%s\t%s\n' "$address" "$version"
  fi
done <<< "$postgres_resources"
