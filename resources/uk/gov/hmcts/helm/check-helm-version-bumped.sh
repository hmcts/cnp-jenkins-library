#!/bin/bash
set -x

CHART_DIRECTORY=${1}-${2}
BRANCH=${3}
USER_NAME=${4}
BEARER_TOKEN=${5}
EMAIL_ID=${6}

git fetch origin master:master

git diff -s --exit-code master charts/"${CHART_DIRECTORY}"/values.yaml

if [ $? -eq 1 ]; then
  echo "Diff in values.yaml detected"
  DIFF_IN_VALUES=true
else
  DIFF_IN_VALUES=false
fi

git diff -s --exit-code master charts/"${CHART_DIRECTORY}"/Chart.yaml

if [ $? -eq 1 ]; then
  echo "Diff in Chart.yaml detected"
  DIFF_IN_CHART=true
else
  DIFF_IN_CHART=false
fi

sed -i -e 's/@hmctspublic/https\:\/\/hmctspublic.azurecr.io\/helm\/v1\/repo\//g' charts/"${CHART_DIRECTORY}"/Chart.yaml
git status charts/"${CHART_DIRECTORY}"/Chart.yaml | grep --quiet charts/"${CHART_DIRECTORY}"/Chart.yaml
if [ $? -eq 0 ]; then
  echo "Updated hmctspublic alias in Chart.yaml"
  ALIAS_UPDATED=true
else
  ALIAS_UPDATED=false
fi


if [[ ${DIFF_IN_VALUES} = 'false' ]]  && [[ ${DIFF_IN_CHART} = 'false' ]]  && [[ ${ALIAS_UPDATED} = 'false' ]] ; then
  echo 'No differences requiring chart version bump detected'
  exit 0
fi

git diff master charts/"${CHART_DIRECTORY}"/Chart.yaml | grep --quiet '+version'

if [ $? -eq 0 ]; then
  echo "Chart.yaml version has been bumped :)"
  CHART_BUMPED=false
else
  CHART_BUMPED=true
  echo "==================================================================="
  echo "=====  Version is not bumped in Chart.yaml file    ====="
  echo "=====  This is required as you changed something in           ====="
  echo "=====  either values.yaml or requirements.yaml                ====="
  echo "=====  Jenkins will bump the version and commit for you.      ====="
  echo "==================================================================="

  CHART_VERSION=$(cat charts/"${CHART_DIRECTORY}"/Chart.yaml | grep ^version | cut -d ':' -f 2 | sed -e 's/^[[:space:]]*//')
  NEW_VERSION=$(echo $CHART_VERSION | awk -F. '{$NF = $NF + 1;} 1' | sed 's/ /./g' )
  sed -i -e "s/^version: $CHART_VERSION/version: $NEW_VERSION/" charts/"${CHART_DIRECTORY}"/Chart.yaml

fi

if [[ ${CHART_BUMPED} = 'true' ]]  || [[ ${ALIAS_UPDATED} = 'true' ]] ; then
  git fetch origin $BRANCH:$BRANCH
  # Only modify URL if it doesn't already contain credentials
  REMOTE_URL=$(git config remote.origin.url)
  if [[ ! "$REMOTE_URL" =~ @github\.com ]]; then
    git remote set-url origin $(echo "$REMOTE_URL" | sed "s/github.com/${USER_NAME}:${BEARER_TOKEN}@github.com/g")
  fi
  git config --global user.name ${USER_NAME}
  git config --global user.email ${GIT_APP_EMAIL_ID}

  git add charts/"${CHART_DIRECTORY}"/Chart.yaml
  git commit -m "Bumping chart version/ fixing aliases"
  git push origin HEAD:$BRANCH
  exit 1
fi
