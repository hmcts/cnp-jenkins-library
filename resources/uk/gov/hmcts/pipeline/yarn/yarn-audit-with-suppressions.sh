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

print_borked_known_issues() {
cat <<'EOF'

  You have an invalid yarn-audit-known-issues file.

  The command to suppress known vulnerabilities has changed.

  Please now use the following:

  `yarn npm audit --recursive --environment production --json > yarn-audit-known-issues`

EOF
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

# Perform yarn audit and process the results
today=$(date +"%s")
# 2024-02-21
exclude_until="1708502400"

if [ "$today" -gt "$exclude_until" ]; then
  echo "DEBUG: Running yarn audit without ignore..."
  yarn npm audit --recursive --environment production --json > yarn-audit-result || true
else
  echo "DEBUG: Running yarn audit with ignore 1096460..."
  yarn npm audit --recursive --environment production --json --ignore 1096460 > yarn-audit-result || true
fi

echo "DEBUG: Yarn version check - YARN_VERSION=$YARN_VERSION"
echo "DEBUG: yarn --version output: $(yarn --version)"
echo "DEBUG: First line of yarn-audit-result:"
head -1 yarn-audit-result

if [ "$YARN_VERSION" == "4" ]; then
  echo "DEBUG: Using format-v4-audit.cjs converter..."
  cat yarn-audit-result | node format-v4-audit.cjs > yarn-audit-result-formatted
else
  echo "DEBUG: Copying yarn-audit-result as-is..."
  cp yarn-audit-result yarn-audit-result-formatted
fi

echo "DEBUG: First line of yarn-audit-result-formatted:"
head -10 yarn-audit-result-formatted

echo "DEBUG: Testing jq command on yarn-audit-result-formatted..."
jq -cr '.advisories | to_entries[].value' < yarn-audit-result-formatted
jq -cr '.advisories | to_entries[].value' < yarn-audit-result-formatted | sort > sorted-yarn-audit-issues

# Check if there were any vulnerabilities
if [[ ! -s sorted-yarn-audit-issues ]];  then
  echo "No vulnerabilities found in project dependencies."

  # Check for unneeded suppressions when no vulnerabilities are present
  if [ -f yarn-audit-known-issues ]; then
    # Convert JSON array into sorted list of suppressed issues
    if [ "$YARN_VERSION" == "4" ]; then
      cat yarn-audit-known-issues | node format-v4-audit.cjs > yarn-audit-known-issues-formatted
    else
      cp yarn-audit-known-issues yarn-audit-known-issues-formatted
    fi

    jq -cr '.advisories | to_entries[].value' yarn-audit-known-issues-formatted \
    | sort > sorted-yarn-audit-known-issues

    # When no vulnerabilities are found, all suppressions are unneeded
    check_for_unneeded_suppressions
  fi

  exit 0
fi

# Check if there are known vulnerabilities
if [ ! -f yarn-audit-known-issues ]; then
  source prettyPrintAudit.sh sorted-yarn-audit-issues
  print_guidance
  exit 1
else
  # Test for old format of yarn-audit-known-issues
  echo "DEBUG: Processing yarn-audit-known-issues..."
  echo "DEBUG: First line of yarn-audit-known-issues:"
  head -1 yarn-audit-known-issues
  
  if [ "$YARN_VERSION" == "4" ]; then
    echo "DEBUG: Converting yarn-audit-known-issues with format-v4-audit.cjs..."
    cat yarn-audit-known-issues | node format-v4-audit.cjs > yarn-audit-known-issues-formatted
  else
    echo "DEBUG: Copying yarn-audit-known-issues as-is..."
    cp yarn-audit-known-issues yarn-audit-known-issues-formatted
  fi
  
  echo "DEBUG: First line of yarn-audit-known-issues-formatted:"
  head -1 yarn-audit-known-issues-formatted

  # Check if the file contains valid JSON (either old format or new NDJSON format)
  if ! jq empty yarn-audit-known-issues-formatted >/dev/null 2>&1; then
    # Try as newline-delimited JSON (new format)
    if ! jq -s empty yarn-audit-known-issues-formatted >/dev/null 2>&1; then
      echo "DEBUG: yarn-audit-known-issues-formatted contains invalid JSON"
      print_borked_known_issues
      exit 1
    fi
    echo "DEBUG: Detected newline-delimited JSON format (Yarn v4+)"
  else
    # Check if it has the old expected structure
    if jq -e 'has("actions") and has("advisories") and has("metadata")' yarn-audit-known-issues-formatted >/dev/null 2>&1; then
      echo "DEBUG: Detected old JSON format with actions/advisories/metadata"
    else
      echo "DEBUG: Valid JSON but not old format, assuming new format"
    fi
  fi

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

  # Check for unneeded suppressions
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

    source prettyPrintAudit.sh active_suppressions
    exit 0
  fi
fi
