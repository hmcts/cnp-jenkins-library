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
# This script is mostly used by the automated Jenkins pipelines.
# However, it should still work locally if you run it like so:
# - Change into the repository that you want to run the script against (containing the NodeJS Yarn project and yarn-audit-known-issues file):
#   cd /path-to-your/repo
# - If your project is using Yarn 4, ensure to export the YARN_VERSION variable:
#   export YARN_VERSION=4
# - Export your GitHub Bearer token to make sure advisory data can be fetched:
#   export GITHUB_BEARER_TOKEN=$(gh auth token)
# - Execute the script by providing the full path to it in the cnp-jenkins-library:
#   - /path-to-your/cnp-jenkins-library/resources/uk/gov/hmcts/pipeline/yarn/yarn-audit-with-suppressions.sh local
#
# Exit Codes:
# 0 - Success, no vulnerabilities found or only known vulnerabilities found
# 1 - Unhandled vulnerabilities were found
################################################################################


# Exit script on error
set -e

src_dir="."
# Auto-detect src_dir only if user provides "local" argument
if [ "$1" == "local" ]; then
  src_dir="$(cd "$(dirname "$0")" && pwd)"
fi

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
  echo "You have an older version of Yarn that has issues with --recursive flag, so we will run '--all' instead"
}

# Function to check for unneeded suppressions using comm utility
check_for_unneeded_suppressions() {
  # -13 suppresses output of lines unique to first file and lines common to both, only shows unique lines in second file
  comm -13 sorted-yarn-audit-issues sorted-yarn-audit-known-issues > unneeded_suppressions

  if [[ -s unneeded_suppressions ]]; then
    echo "WARNING: Unneeded suppressions found. You can safely delete these from the yarn-audit-known-issues file:"
    source "${src_dir}/prettyPrintAudit.sh" unneeded_suppressions
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

check_audit_file_format() {
  local file="$1"
  echo "Checking file: $file"
  if jq 'has("actions", "advisories", "metadata")' "$file" | grep -q true; then
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
  if [ "$OLD_AUDIT_FORMAT" -eq 0 ]; then
    echo "Formatting Yarn Audit report from 4.x to Yarn 3.x audit format and enriching with GitHub Advisory data"
    cat yarn-audit-result | node "${src_dir}/transform-v4-to-v3-audit.cjs" > yarn-audit-result-formatted
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
    cat yarn-audit-known-issues | node "${src_dir}/transform-v4-to-v3-audit.cjs" > yarn-audit-known-issues-formatted
    jq -cr '.advisories | to_entries[].value' yarn-audit-known-issues-formatted \
              | sort > sorted-yarn-audit-known-issues

    # When no vulnerabilities are found, all suppressions are unneeded
    if [ -f yarn-audit-known-issues ]; then
      echo "WARNING: Unneeded suppressions found. You can safely delete these from the yarn-audit-known-issues file:"
      source "${src_dir}/prettyPrintAudit.sh" sorted-yarn-audit-known-issues
    fi
  fi
  echo "No vulnerabilities found, exiting with code 0"
  exit 0
fi

# check vulnerabilities against known issues and newly found vulnerabilities in sorted-yarn-audit-issues
if [ ! -f yarn-audit-known-issues ]; then
  source "${src_dir}/prettyPrintAudit.sh" sorted-yarn-audit-issues
  print_guidance
  exit 1
else
  # Test for old format of yarn-audit-known-issues
  if [ "$YARN_VERSION" == "4" ]; then
    cat yarn-audit-known-issues | node "${src_dir}/transform-v4-to-v3-audit.cjs" > yarn-audit-known-issues-formatted
  else
    cp yarn-audit-known-issues yarn-audit-known-issues-formatted
  fi

  check_file_valid_json yarn-audit-known-issues
  if ! jq -e 'type == "object" and has("actions") and has("advisories") and has("metadata")' yarn-audit-known-issues-formatted >/dev/null 2>&1; then
    echo "❌ Invalid or unexpected yarn-audit-known-issues-formatted structure (expected Yarn 4 format)"
    print_borked_known_issues
    exit 1
  fi

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

  # Check each issue in sorted-yarn-audit-issues is also present in sorted-yarn-audit-known-issues using comm utility
  # -23 suppresses output of lines unique to second file and lines common to both, only shows unique lines in first file
  comm -23 sorted-yarn-audit-issues sorted-yarn-audit-known-issues > new_vulnerabilities

  # checks for "unneeded suppressions" - vulnerabilities that were previously suppressed in the yarn-audit-known-issues
  # file but are no longer present in the current audit results
  check_for_unneeded_suppressions

  # Check if there were any new vulnerabilities
  if [[ -s new_vulnerabilities ]]; then
    echo "Unsuppressed vulnerabilities found:"
    source "${src_dir}/prettyPrintAudit.sh" new_vulnerabilities
    print_guidance
    exit 1
  else
    echo "Active suppressed vulnerabilities:"
    # Find actively suppressed vulnerabilities to display in the output
    # -12 suppresses output of lines unique to first and second file, only shows lines common to both files
    comm -12 sorted-yarn-audit-issues sorted-yarn-audit-known-issues > active_suppressions
    # check active_suppressions is not empty before trying to pretty print it
    if [ ! -s active_suppressions ]; then
      echo "No active suppressed vulnerabilities."
    else
      source "${src_dir}/prettyPrintAudit.sh" active_suppressions
    fi
    exit 0
  fi
fi
