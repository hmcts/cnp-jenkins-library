#!/bin/bash
set -euo pipefail

plan_file="${1:?Terraform plan file is required}"
deprecated_sku="${2:?Deprecated SKU generation is required}"
if [[ ! "$deprecated_sku" =~ ^v[0-9]+$ ]]; then
  echo "Invalid deprecated SKU generation: $deprecated_sku" >&2
  exit 2
fi
generation="${deprecated_sku:1}"

postgres_resources=$(terraform show -json "$plan_file" | jq -r '
  .planned_values.root_module
  | [.. | objects
    | select(.mode? == "managed" and .type? == "azurerm_postgresql_flexible_server")
    | [(.address // "unknown"), (.values.sku_name // "unknown")]
    | @tsv]
  | .[]')

while IFS=$'\t' read -r address sku; do
  [[ -n "$address" ]] || continue
  if [[ "$sku" == "unknown" ]]; then
    printf 'Unable to determine PostgreSQL SKU for Terraform resource %s\n' "$address" >&2
    exit 2
  elif [[ "$sku" =~ _[vV]${generation}$ ]]; then
    printf '%s\t%s\n' "$address" "$sku"
  fi
done <<< "$postgres_resources"
