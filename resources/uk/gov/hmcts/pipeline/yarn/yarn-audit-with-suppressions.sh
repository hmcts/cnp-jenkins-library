#!/usr/bin/env bash

# This is a wrapper script for yarn audit that allows suppressing
# vulnerabilities without a fix

cleanup() {
rm -f new_vulnerabilities sorted-yarn-audit-issues sorted-yarn-audit-known-issues
}

print_guidance() {
cat <<'EOF'

  Security vulnerabilities were found that were not ignored.

  Check to see if these vulnerabilities apply to production

  and/or if they have fixes available. If they do not have

  fixes and they do not apply to production, you may ignore them

  To ignore these vulnerabilities, run:
  `yarn npm audit --recursive --environment production --json > yarn-audit-known-issues`

  and commit the yarn-audit-known-issues file

EOF
}

yarn npm audit --recursive --environment production --json \
| jq -cr '.advisories | to_entries[].value' \
| sort > sorted-yarn-audit-issues

if [[ ! -s sorted-yarn-audit-issues ]];  then
  echo "Congratulations! No vulnerable dependencies found!"
  exit 0
fi

if [ ! -f yarn-audit-known-issues ]; then
  source prettyPrintAudit.sh sorted-yarn-audit-issues
  print_guidance
  cleanup
else
  # Edge case for when audit returns in different orders for the two files
  # Convert JSON arrays into sorted lists of issues.
  jq -cr '.advisories | to_entries[].value' yarn-audit-known-issues \
  | sort > sorted-yarn-audit-known-issues
  # Edge case for when known-issues file is a proper superset of result file.
  # Check each issue in sorted_yarn-audit-result is also present in sorted_yarn-audit-known-issues
  while IFS= read -r line; do
    if ! grep -Fxq "$line" sorted-yarn-audit-known-issues; then
      echo "$line" >> new-vulnerabilities
    fi
  done < sorted-yarn-audit-issues

  if [[ ! -s new-vulnerabilities ]];  then
    # If new vulnerabilities were found, exit with an error status
    echo "Unsuppressed vulnerabilities found: "
    source prettyPrintAudit.sh new-vulnerabilities
    print_guidance
    cleanup
    exit 1
  else
    echo "Ignoring known vulnerabilities."
    cleanup
    exit 0
  fi
fi
