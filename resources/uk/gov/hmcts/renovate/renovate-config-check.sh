#!/bin/bash
set -x

FILE_PATH=""
if [[ -f .github/renovate.json ]]; then
    FILE_PATH=".github/renovate.json"
elif [[ -f renovate.json ]]; then
    FILE_PATH="renovate.json"
else
    echo "Cannot find renovate.json in .github or root folders"
    exit 1
fi

if grep -iq "enabledManagers" "$FILE_PATH"; then
    echo "Usage of enabledManagers is deprecated"
    exit 1
fi

if ! grep -iq "local>hmcts/.github:renovate-config" "$FILE_PATH"; then
    echo "Renovate config should extend local>hmcts/.github:renovate-config"
    exit 1
fi
