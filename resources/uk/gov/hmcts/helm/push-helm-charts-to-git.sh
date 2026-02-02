#!/bin/bash
set -x

CHART_DIRECTORY=${1}
CHART_NAME=${2}
VERSION=${3}

RETRIES=3
DELAY=5
COUNT=0

if [ -d "hmcts-charts" ]; then
  rm -rf hmcts-charts
fi

while [ $COUNT -lt $RETRIES ]; do
  if git clone https://github.com/hmcts/hmcts-charts.git; then
    COUNT=0
    break
  fi
  COUNT=$(( $COUNT + 1))
  if [ "$COUNT" = "$RETRIES" ]; then
    exit 1
  fi
  sleep $DELAY
done

if cd hmcts-charts; then
  if [ -d "${CHART_NAME}" ]; then
    GIT_CHART_VERSION=$(helm inspect chart "${CHART_NAME}" | grep ^version | cut -d  ':' -f 2 | tr -d '[:space:]')
    if [ "${GIT_CHART_VERSION}" = "${VERSION}" ]; then
      echo "No differences in the charts. Nothing to publish" 1>&2
      exit 0
    fi
  else
     echo "Chart version ${GIT_CHART_VERSION} not published to git yet" 1>&2
  fi
  
  if rsync -a --delete-after "../${CHART_DIRECTORY}/" "./stable/${CHART_NAME}"; then

    if [ -d ./stable/${CHART_NAME}/charts ]; then 
      rm -rf ./stable/${CHART_NAME}/charts
      if [[ $? -ne 0 ]];then
        echo "Unable to delete stable/${CHART_NAME}/charts directory" 1>&2
      fi
    fi

    if [ -e ./stable/${CHART_NAME}/Chart.lock ];then
      rm -f ./stable/${CHART_NAME}/Chart.lock
      if [[ $? -ne 0 ]];then
        echo "Unable to delete stable/${CHART_NAME}/Chart.lock " 1>&2
      fi
    fi

    if [ -e ./stable/${CHART_NAME}/requirements.lock ];then
      rm -f ./stable/${CHART_NAME}/requirements.lock
      if [[ $? -ne 0 ]];then
        echo "Unable to delete stable/${CHART_NAME}/requirements.lock " 1>&2
      fi
    fi

    # Only modify URL if it doesn't already contain credentials
    REMOTE_URL=$(git config remote.origin.url)
    if [[ ! "$REMOTE_URL" =~ @github\.com ]]; then
      git remote set-url origin $(echo "$REMOTE_URL" | sed "s/github.com/${GIT_CREDENTIALS_ID}:${BEARER_TOKEN}@github.com/g")
    fi
    git config --global user.name "${GIT_CREDENTIALS_ID}"
    git config --global user.email "${GIT_APP_EMAIL_ID}"
    git add "stable/${CHART_NAME}/"
    git commit -m "Auto-release ${CHART_NAME} ${VERSION}"

    while [ $COUNT -lt $RETRIES ]; do
      git pull origin master
      if git push origin HEAD:master; then
        echo "Chart published successfully with ${GIT_CHART_VERSION}"
        COUNT=0
        break
      else
        echo "Failed to publish chart to git. Retry count $COUNT of $RETRIES"
      fi
    COUNT=$(( $COUNT + 1))
    if [ "$COUNT" = "$RETRIES" ]; then
      exit 1
    fi
    sleep $DELAY
    done

  else
    echo "Could not copy charts directory! Aborting" 1>&2
    exit 1
  fi
else
  echo "Could not change directory! Aborting" 1>&2
  exit 1
fi
