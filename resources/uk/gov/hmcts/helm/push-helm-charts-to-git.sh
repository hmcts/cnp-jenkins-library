#!/bin/bash
set -x

CHART_DIRECTORY=${1}
CHART_NAME=${2}
USER_NAME=${3}
EMAIL_ID=${4}
VERSION=${5}

if [ -d "hmcts-charts" ]; then
  rm -rf hmcts-charts
fi

git clone https://github.com/hmcts/hmcts-charts.git

if cd hmcts-charts; then
  if [ -d "${CHART_NAME}" ]; then
    GIT_CHART_VERSION=$(helm inspect chart "${CHART_NAME}" | grep ^version | cut -d  ':' -f 2 | tr -d '[:space:]')
    if [ "${GIT_CHART_VERSION}" = "${VERSION}" ]; then
      echo "No differences in the charts. Nothing to publish." 1>&2
      exit 0
    fi
  else
     echo "Chart not published to git yet." 1>&2
  fi
  
  if cp -R "../${CHART_DIRECTORY}" .; then
    git config --global user.name "${USER_NAME}"
    git config --global user.email "${EMAIL_ID}"
    git add "${CHART_NAME}/"
    git commit -m "chart push to git"

    if git push origin HEAD:master; then
      echo "Chart published successfully."
    else
      echo "Failed to publish chart to git."
    fi

  else
    echo "Could not copy charts directory! Aborting." 1>&2
    exit 1
  fi
else
  echo "Could not change directory! Aborting." 1>&2
  exit 1
fi
