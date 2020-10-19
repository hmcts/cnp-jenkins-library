#!/usr/bin/env bash

# This is a wrapper script for yarn audit that allows suppressing
# vulnerabilities without a fix
# Source: https://github.com/yarnpkg/yarn/issues/6669#issuecomment-463684767
# Note: There's an upstream PR for adding this functionality: https://github.com/yarnpkg/yarn/pull/8223

set +e
yarn audit --groups dependencies
result=$?

yarn audit --groups dependencies --json > yarn-audit-issues-result

set -e

if [ "$result" != 0 ]; then
  if [ -f yarn-audit-known-issues ]; then
    set +e
    grep auditAdvisory yarn-audit-issues-result > yarn-audit-issues
    set -e

    if diff -q yarn-audit-known-issues yarn-audit-issues > /dev/null 2>&1; then
      rm -f yarn-audit-issues
      echo
      echo Ignorning known vulnerabilities
      exit 0
    fi
  fi

  echo
  echo Security vulnerabilities were found that were not ignored
  echo
  echo Check to see if these vulnerabilities apply to production
  echo and/or if they have fixes available. If they do not have
  echo fixes and they do not apply to production, you may ignore them
  echo
  echo To ignore these vulnerabilities, run:
  echo
  echo "yarn audit --groups dependencies --json | grep auditAdvisory > yarn-audit-known-issues"
  echo
  echo and commit the yarn-audit-known-issues file

  rm -f yarn-audit-issues

  exit "$result"
fi
