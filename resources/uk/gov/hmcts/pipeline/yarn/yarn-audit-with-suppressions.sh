#!/usr/bin/env bash
# This is a wrapper script for yarn audit that allows suppressing
# vulnerabilities without a fix

set +e
yarn npm audit --recursive --environment production
result=$?
yarn npm audit --recursive --environment production --json > yarn-audit-result
set -e

if [ "$result" != 0 ]; then
  if [ -f yarn-audit-known-issues ]; then
    set +e
    cat yarn-audit-result | jq -cr '.advisories| to_entries[] | {"type": "auditAdvisory", "data": { "advisory": .value }}' > yarn-audit-issues
    set -e

    # Edge case for when audit returns in different orders for the two files
    # Convert JSON arrays into sorted lists of issues
    jq -c '.[]' yarn-audit-result | sort > sorted_yarn-audit-result
    jq -c '.[]' yarn-audit-known-issues | sort > sorted_yarn-audit-known-issues

    # Edge case for when known-issues file is a proper superset of result file.
    # Check each issue in sorted_yarn-audit-result is also present in sorted_yarn-audit-known-issues
    while IFS= read -r line; do
      if ! grep -Fxq "$line" sorted_yarn-audit-known-issues; then
        new_vulnerability_found=true
      fi
    done < sorted_yarn-audit-result

    # If new vulnerabilities were found, exit with an error status
    if [ "$new_vulnerability_found" = true ]; then
      rm sorted_yarn-audit-result sorted_yarn-audit-known-issues
      exit 1
    fi

      # Clean up sorted files
      rm sorted_yarn-audit-result sorted_yarn-audit-known-issues
      rm -f yarn-audit-issues
      echo
      echo Ignoring known vulnerabilities
      exit 0
    fi
  fi
  cat <<'EOF'
    Security vulnerabilities were found that were not ignored

    Check to see if these vulnerabilities apply to production
    and/or if they have fixes available. If they do not have
    fixes and they do not apply to production, you may ignore them

    To ignore these vulnerabilities, run:

    `yarn npm audit --environment production --recursive --json | jq -cr '.advisories| to_entries[] | {"type": "auditAdvisory", "data": { "advisory": .value }}' > yarn-audit-known-issues`

    and commit the yarn-audit-known-issues file
EOF

  rm -f yarn-audit-issues

  exit "$result"
fi
