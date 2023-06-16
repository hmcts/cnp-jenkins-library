#!/usr/bin/env bash

################################################################################
# Yarn Audit Wrapper
#
# This script performs a security audit on Yarn dependencies, with the ability
# to suppress vulnerabilities that are known and have no fix. The audit results
# are output in JSON format. Any new vulnerabilities are reported to the user.
#
# Required Dependencies:
# - jq: A lightweight and flexible command-line JSON processor
# - yarn: Fast, reliable, and secure dependency management
# - prettyPrintAudit.sh: Script to pretty print the audit results
#
# Usage:
# Execute the script in the directory containing your project and yarn-audit-known-issues file:
# ./audit_wrapper.sh
#
# Exit Codes:
# 0 - Success, no vulnerabilities found or only known vulnerabilities found
# 1 - Unhandled vulnerabilities were found
################################################################################

# Temporary files cleanup function
cleanup() {
rm -f new_vulnerabilities sorted-yarn-audit-issues sorted-yarn-audit-known-issues
}

# Function to print guidance message in case of found vulnerabilities
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

# Perform yarn audit and process the results
yarn npm audit --recursive --environment production --json \
| jq -cr '.advisories | to_entries[].value' \
| sort > sorted-yarn-audit-issues

# Check if there were any vulnerabilities
if [[ ! -s sorted-yarn-audit-issues ]];  then
  echo "Congratulations! No vulnerable dependencies found!"
  exit 0
fi

# Check if there are known vulnerabilities
if [ ! -f yarn-audit-known-issues ]; then
  source prettyPrintAudit.sh sorted-yarn-audit-issues
  print_guidance
  cleanup
else
  # Handle edge case for when audit returns in different orders for the two files
  # Convert JSON array into sorted list of issues.
  jq -cr '.advisories | to_entries[].value' yarn-audit-known-issues \
  | sort > sorted-yarn-audit-known-issues

  # Edge case for when known-issues file is a proper superset of result file.
  # Check each issue in sorted-yarn-audit-result is also present in sorted-yarn-audit-known-issues
  while IFS= read -r line; do
    if ! grep -Fxq "$line" sorted-yarn-audit-known-issues; then
      echo "$line" >> new_vulnerabilities
    fi
  done < sorted-yarn-audit-issues

  # Check if there were any new vulnerabilities
  if [[ ! -s new_vulnerabilities ]];  then
    # If new vulnerabilities were found, exit with an error status
    echo "Unsuppressed vulnerabilities found: "
    source prettyPrintAudit.sh new_vulnerabilities
    print_guidance
    cleanup
    exit 1
  else
    echo "Ignoring known vulnerabilities."
    cleanup
    exit 0
  fi
fi

