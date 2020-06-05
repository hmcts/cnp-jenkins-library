#!/bin/bash
set -x

CHART_DIRECTORY=${1}-${2}
BRANCH=${3}
USER_NAME=${4}
BEARER_TOKEN=${5}
EMAIL_ID=${6}

git fetch origin master:master

git diff -s --exit-code origin/master charts/"${CHART_DIRECTORY}"/values.yaml

if [ $? -eq 1 ]; then
  echo "Diff in values.yaml detected"
  DIFF_IN_VALUES=true
else
  DIFF_IN_VALUES=false
fi

git diff -s --exit-code origin/master charts/"${CHART_DIRECTORY}"/Chart.yaml

if [ $? -eq 1 ]; then
  echo "Diff in Chart.yaml detected"
  DIFF_IN_CHART=true
else
  DIFF_IN_CHART=false
fi

DIFF_IN_REQUIREMENTS=false
if [[ -f charts/"${CHART_DIRECTORY}"/requirements.yaml ]]
then
  git diff -s --exit-code origin/master charts/"${CHART_DIRECTORY}"/requirements.yaml
  if [ $? -eq 1 ]; then
     echo "Diff in requirements.yaml detected"
     DIFF_IN_REQUIREMENTS=true
   fi
fi

if [[ ${DIFF_IN_VALUES} = 'false' ]] && [[ ${DIFF_IN_REQUIREMENTS} = 'false' ]] && [[ ${DIFF_IN_CHART} = 'false' ]] ; then
  echo 'No differences requiring chart version bump detected'
  exit 0
fi

git diff origin/master charts/"${CHART_DIRECTORY}"/Chart.yaml | grep --quiet '+version'

if [ $? -eq 0 ]; then
  echo "Chart.yaml version has been bumped :)"
else
  echo "==================================================================="
  echo "=====  Version is not bumped in Chart.yaml file    ====="
  echo "=====  This is required as you changed something in           ====="
  echo "=====  either values.yaml or requirements.yaml                ====="
  echo "=====  Jenkins will bump the version and commit for you.      ====="
  echo "==================================================================="

  git fetch origin $BRANCH:$BRANCH
  git remote set-url origin $(git config remote.origin.url | sed "s/github.com/${USER_NAME}:${BEARER_TOKEN}@github.com/g")
  git config --global user.name ${USER_NAME}
  git config --global user.email ${GIT_APP_EMAIL_ID}

  CHART_VERSION=$(cat charts/"${CHART_DIRECTORY}"/Chart.yaml | grep ^version | cut -d ':' -f 2 | sed -e 's/^[[:space:]]*//')
  NEW_VERSION=$(echo $CHART_VERSION | awk -F. '{$NF = $NF + 1;} 1' | sed 's/ /./g' )
  sed -i -e "s/^version: $CHART_VERSION/version: $NEW_VERSION/" charts/"${CHART_DIRECTORY}"/Chart.yaml

  git add charts/"${CHART_DIRECTORY}"/Chart.yaml
  git commit -m "Bumping chart version"
  git push origin HEAD:$BRANCH
  exit 1
fi
