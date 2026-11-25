#!/usr/bin/env bash
set -euo pipefail

minimum_version="${1:?Minimum PostgreSQL version is required}"
plan_file="${2:-tfplan}"

if [[ ! "$minimum_version" =~ ^[0-9]+$ ]]; then
  echo "Invalid minimum PostgreSQL version: $minimum_version" >&2
  exit 2
fi

versions=$(terraform show -json "$plan_file" |
  jq -r '
    .. | objects
    | select(.type? == "azurerm_postgresql_server" or .type? == "azurerm_postgresql_flexible_server")
    | .values.version? // empty
    | tostring
  ')

while IFS= read -r version; do
  [[ "$version" =~ ^[0-9]+$ ]] || continue

  if (( 10#$version < 10#$minimum_version )); then
    printf 'PostgreSQL %s is below the required major version %s.\n' "$version" "$minimum_version"
  fi
done <<< "$versions"
