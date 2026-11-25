#!/bin/bash
set -euo pipefail

plan_file="${1:?Terraform plan file is required}"

postgres_resources=$(terraform show -json "$plan_file" | jq -r '
  if (.resource_changes | type) != "array" then
    error("Terraform plan has no resource_changes array")
  else
    .resource_changes[]
    | select(.mode == "managed" and .type == "azurerm_postgresql_flexible_server")
    | .address as $address
    | .change as $change
    | [
        (if $change.before != null and $change.before.sku_name != null
         then [$address, "current", $change.before.sku_name] else empty end),
        (if $change.after != null and $change.after.sku_name != null
         then [$address, "planned", $change.after.sku_name] else empty end),
        (if $change.after != null and
            ($change.after_unknown.sku_name == true or $change.after.sku_name == null)
         then [$address, "unknown", "unknown"] else empty end)
      ][]
    | @tsv
  end')

while IFS=$'\t' read -r address state sku; do
  [[ -n "$address" ]] || continue
  if [[ "$state" == "unknown" ]]; then
    printf 'WARNING: Planned PostgreSQL SKU is unknown for %s; it cannot be checked yet.\n' "$address" >&2
  elif [[ "$sku" =~ _[vV]3$ ]]; then
    printf '%s\t%s\t%s\n' "$address" "$state" "$sku"
  fi
done <<< "$postgres_resources"
