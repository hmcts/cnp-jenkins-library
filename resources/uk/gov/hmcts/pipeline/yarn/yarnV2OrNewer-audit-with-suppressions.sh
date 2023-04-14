#!/usr/bin/env bash
# This is a wrapper script for yarn audit that allows suppressing
# vulnerabilities without a fix

set +e
today=$(date +%Y-%m-%d)
enddate="2023-05-15"

if [[ "$today" > "$enddate" ]]; then
yarn npm audit --recursive --environment production
result=$?
yarn npm audit --recursive --environment production --json > yarn-audit-result
else
 yarn npm audit --environment production
 result=$?
 yarn npm audit --environment production --json > yarn-audit-result
fi

set -e

if [ "$result" != 0 ]; then
  if [ -f yarn-audit-known-issues ]; then
    set +e
    cat yarn-audit-result | jq -cr '.advisories| to_entries[] | {"type": "auditAdvisory", "data": { "advisory": .value }}' > yarn-audit-issues
    set -e

    if diff -q yarn-audit-known-issues yarn-audit-issues > /dev/null 2>&1; then
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

    `yarn npm audit --environment production --json | jq -cr '.advisories| to_entries[] | {"type": "auditAdvisory", "data": { "advisory": .value }}' > yarn-audit-known-issues`

    and commit the yarn-audit-known-issues file
EOF

  rm -f yarn-audit-issues

  exit "$result"
fi
