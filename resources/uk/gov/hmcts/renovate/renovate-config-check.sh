#!/bin/bash
set -x

if [[ ! -f .github/renovate.json ]]
then
    echo "Cannot find renovate.json in .github folder"
    exit 1
else
  if [ $(grep -ic "enabledManagers" .github/renovate.json) -ge 1 ]
  then
    echo "Usage of enabledManagers is deprecated"
    exit 1
  fi
  if [ $(grep -ic "local>hmcts/.github:renovate-config" .github/renovate.json) -eq  0 ]
  then
    echo "All Renovate config should extend local>hmcts/.github:renovate-config"
    exit 1
  fi
fi
