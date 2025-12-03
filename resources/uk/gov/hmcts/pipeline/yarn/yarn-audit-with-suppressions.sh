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
# Mostly used in the pipeline but feel free to use the script locally, should still work there:
# Execute the script in the directory containing your project and yarn-audit-known-issues file:
# ./yarn-audit-with-suppressions.sh
#
# Exit Codes:
# 0 - Success, no vulnerabilities found or only known vulnerabilities found
# 1 - Unhandled vulnerabilities were found
################################################################################


# Exit script on error
set -e

# Check for dependencies
command -v yarn >/dev/null 2>&1 || { echo >&2 "yarn is required but it's not installed. Aborting."; exit 1; }
command -v jq >/dev/null 2>&1 || { echo >&2 "jq is required but it's not installed. Aborting."; exit 1; }

FOUND_VULNERABILITIES=0 # Flag to indicate if unhandled vulnerabilities are found; 0 = none, 1 = found
OLD_AUDIT_FORMAT=0  # Flag to indicate if old audit format is detected; 0 = no, 1 = yes

# Function to print guidance message in case of found vulnerabilities
print_guidance() {
cat <<'EOF'

  Security vulnerabilities were found that were not ignored.

  Check to see if these vulnerabilities apply to production

  and/or if they have fixes available. If they do not have

  fixes and they do not apply to production, you may ignore them

  To ignore these vulnerabilities, run:

  if Yarn 4
  `yarn npm audit --recursive --environment production --json > yarn-audit-known-issues`
  else
  `yarn npm audit --all --environment production --json > yarn-audit-known-issues`

  and commit the yarn-audit-known-issues file

EOF
}

print_borked_known_issues() {
cat <<'EOF'

  You have an invalid yarn-audit-known-issues file.

  The command to suppress known vulnerabilities has changed.

  Please now use the following:

  if Yarn 4
    `yarn npm audit --recursive --environment production --json > yarn-audit-known-issues`
  else
    `yarn npm audit --all --environment production --json > yarn-audit-known-issues`
EOF
}

print_upgrade_yarn4() {
 echo "You have an older version of Yarn, please upgrade to V4 (https://yarnpkg.com/blog/release/4.0)"
}

# Function to check for unneeded suppressions
check_for_unneeded_suppressions() {
  while IFS= read -r line; do
    if ! grep -Fxq "$line" sorted-yarn-audit-issues; then
      echo "$line" >> unneeded_suppressions
    fi
  done < sorted-yarn-audit-known-issues

  if [[ -s unneeded_suppressions ]]; then
    echo "WARNING: Unneeded suppressions found. You can safely delete these from the yarn-audit-known-issues file:"
    source prettyPrintAudit.sh unneeded_suppressions
  fi
}

check_vulnerabilities() {
  local file="$1"
  if [ ! -s "$file" ]; then
    FOUND_VULNERABILITIES=0
  else
    VULNERABILITY_COUNT=$(cat "$file" | jq '.metadata.vulnerabilities | .info + .low + .moderate + .high + .critical // 0')
    # Check if the total count is greater than 0.
    if [ "${VULNERABILITY_COUNT:-0}" -gt 0 ]; then
      FOUND_VULNERABILITIES=1
      echo "Vulnerabilities found: $VULNERABILITY_COUNT. See output below for details."
      cat "$file"
    else
      echo "No vulnerabilities found."
      FOUND_VULNERABILITIES=0
    fi
  fi
}

check_file_valid_json() {
  local file="$1"
  if ! jq empty "$file" 2>/dev/null; then
    echo "You have an empty json file: $file."
  else
    echo "$file is valid JSON."
  fi
}

check_audit_file_format(){
  local file="$1"
  echo "Checking file: $file"  # Debugging output
  # has("actions") and has("advisories") and has("metadata")
  # has("actions", "advisories", "metadata")
  if ! jq 'has("actions") and has("advisories") and has("metadata")' "$file" | grep -q true; then
    echo "You have either an old format or empty of audit file: $file."
    echo "test"
    OLD_AUDIT_FORMAT=1
  else
    OLD_AUDIT_FORMAT=0
  fi
}

# Perform yarn audit and process the results
today=$(date +"%s")
# 2024-02-21
exclude_until="1708502400"

yarn_audit_command="yarn npm audit --recursive --environment production --json"
if [ "$YARN_VERSION" != "4" ]; then
  print_upgrade_yarn4
  echo "You have an older version of Yarn that has issues with --recursive flag, so we will run '--all' instead"
  yarn_audit_command="yarn npm audit --all --environment production --json"
fi

if [ "$today" -gt "$exclude_until" ]; then
  # run yarn audit command
  $yarn_audit_command > yarn-audit-result || true
else
  # add "--ignore 1096460" to the yarn audit command
  echo "Excluding CVE-2023-4949 (advisory 1096460) until $exclude_until"
  $yarn_audit_command --ignore 1096460 > yarn-audit-result || true
fi

if [ ! -s yarn-audit-result ]; then
  echo "yarn audit returned no results, assuming no vulnerabilities found"
  FOUND_VULNERABILITIES=0
else
  check_file_valid_json yarn-audit-result
  check_audit_file_format yarn-audit-result
  if [ "$OLD_AUDIT_FORMAT" -eq 1 ]; then
    echo "yarn audit returned an old format, try to format the audit result file yarn-audit-result"
    cat yarn-audit-result | node format-v4-audit.cjs > yarn-audit-result-formatted
  else
    cp yarn-audit-result yarn-audit-result-formatted
  fi
fi

check_vulnerabilities yarn-audit-result-formatted

if [ "$FOUND_VULNERABILITIES" -eq 1 ]; then
  jq -cr '(.advisories // {}) | to_entries[].value' < yarn-audit-result-formatted | sort > sorted-yarn-audit-issues
else
  # No vulnerabilities found
  # Check for unneeded suppressions when no vulnerabilities are present
  if [ -f yarn-audit-known-issues ]; then
    check_file_valid_json yarn-audit-known-issues
    # Convert JSON array into sorted list of suppressed issues
    cat yarn-audit-known-issues | node format-v4-audit.cjs > yarn-audit-known-issues-formatted    jq -cr '.advisories | to_entries[].value' yarn-audit-known-issues-formatted \
              | sort > sorted-yarn-audit-known-issues

    # When no vulnerabilities are found, all suppressions are unneeded
    if [ -f yarn-audit-known-issues ]; then
      echo "WARNING: Unneeded suppressions found. You can safely delete these from the yarn-audit-known-issues file:"
      source prettyPrintAudit.sh sorted-yarn-audit-known-issues
    fi
  fi
  echo "No vulnerabilities found, exiting with code 0"
  exit 0
fi

# check vulnerabilities against known issues and newly found vulnerabilities in sorted-yarn-audit-issues
if [ ! -f yarn-audit-known-issues ]; then
  source prettyPrintAudit.sh sorted-yarn-audit-issues
  print_guidance
  exit 1
else
  # Test for old format of yarn-audit-known-issues
  if [ "$YARN_VERSION" == "4" ]; then
    cat yarn-audit-known-issues | node format-v4-audit.cjs > yarn-audit-known-issues-formatted
  else
    cp yarn-audit-known-issues yarn-audit-known-issues-formatted
  fi

  check_file_valid_json yarn-audit-known-issues
  # Convert JSON array into sorted list of suppressed issues
  cat format-v4-audit.cjs
  cat yarn-audit-known-issues | node format-v4-audit.cjs > yarn-audit-known-issues-formatted
  echo "=== DEBUG: yarn-audit-known-issues-formatted ==="
  cat yarn-audit-known-issues-formatted
  echo "==============================================="

  if ! jq -e 'type == "object" and has("actions") and has("advisories") and has("metadata")' yarn-audit-known-issues-formatted >/dev/null 2>&1; then
    echo "❌ Invalid or unexpected yarn-audit-known-issues-formatted structure (expected Yarn 4 format)"
    print_borked_known_issues
    exit 1
  fi
#   if ! jq -e '
#     type == "object" and has("actions") and has("advisories") and (has("metadata") or .metadata == null)
# '   yarn-audit-known-issues-formatted >/dev/null 2>&1; then
 


  # if ! jq 'has("actions", "advisories", "metadata")' yarn-audit-known-issues-formatted | grep -q true; then
  #   print_borked_known_issues
  #   exit 1
  # fi

  # Handle edge case for when audit returns in different orders for the two files
  # Convert JSON array into sorted list of issues.
  jq -cr '.advisories | to_entries[].value' yarn-audit-known-issues-formatted \
  | sort > sorted-yarn-audit-known-issues

  # Retain old data ingestion style for cosmosDB
  jq -cr '.advisories| to_entries[] | {"type": "auditAdvisory", "data": { "advisory": .value }}' yarn-audit-known-issues-formatted > yarn-audit-known-issues-result

  # Check each issue in sorted-yarn-audit-result is also present in sorted-yarn-audit-known-issues
  while IFS= read -r line; do
    if ! grep -Fxq "$line" sorted-yarn-audit-known-issues; then
      echo "$line" >> new_vulnerabilities
    fi
  done < sorted-yarn-audit-issues

  # checks for "unneeded suppressions" - vulnerabilities that were previously suppressed in the yarn-audit-known-issues
  # file but are no longer present in the current audit results
  check_for_unneeded_suppressions

  # Check if there were any new vulnerabilities
  if [[ -s new_vulnerabilities ]]; then
    echo "Unsuppressed vulnerabilities found:"
    source prettyPrintAudit.sh new_vulnerabilities
    print_guidance
    exit 1
  else
    echo "Active suppressed vulnerabilities:"
    while IFS= read -r line; do
        if grep -Fxq "$line" sorted-yarn-audit-issues; then
            echo "$line" >> active_suppressions
        fi
    done < sorted-yarn-audit-known-issues
    # check active_suppressions is not empty before trying to pretty print it
    if [ ! -s active_suppressions ]; then
      echo "No active suppressed vulnerabilities."
    else
      source prettyPrintAudit.sh active_suppressions
    fi
    exit 0
  fi
fi
