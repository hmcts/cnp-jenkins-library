#!/bin/bash
set -euo pipefail

PLAN_FILE="${1:?Terraform plan file is required}"

postgres_resources=$(terraform show -json "$PLAN_FILE" | jq -r '
  .planned_values.root_module
  | [.. | objects
    | select(.mode? == "managed" and .type? == "azurerm_postgresql_flexible_server")
    | [(.address // "unknown"), ((.values.sku_name // "unknown") | tostring)]
    | @tsv]
  | .[]')

while IFS=$'\t' read -r address sku; do
  [[ -n "$address" ]] || continue

  if [[ "$sku" == *_v3 ]]; then
    printf '%s\t%s\n' "$address" "$sku"
  fi
done <<< "$postgres_resources"
