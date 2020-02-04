#!/bin/bash

CHART_DIRECTORY=${1}-${2}

git fetch origin master:master

git diff --no-patch --exit-code master charts/"${CHART_DIRECTORY}"/values.yaml

if [ $? -eq 1 ]; then
  echo "Diff in values.yaml detected"
  DIFF_IN_VALUES=true
else
  DIFF_IN_VALUES=false
fi

git diff --no-patch --exit-code origin/master charts/"${CHART_DIRECTORY}"/requirements.yaml

if [ $? -eq 1 ]; then
  echo "Diff in requirements.yaml detected"
  DIFF_IN_REQUIREMENTS=true
else
  DIFF_IN_REQUIREMENTS=false
fi

if [[ ${DIFF_IN_VALUES} = 'false' ]] && [[ ${DIFF_IN_REQUIREMENTS} = 'false' ]]; then
  echo 'No differences requiring chart version bump detected'
  exit 0
fi

git diff origin/master charts/"${CHART_DIRECTORY}"/Chart.yaml | grep --quiet '+version'

if [ $? -eq 0 ]; then
  echo "Chart.yaml version has been bumped :)"
else
  echo "==================================================================="
  echo "=====  Please increase the version in your Chart.yaml file    ====="
  echo "=====  This is required as you changed something in           ====="
  echo "=====  either values.yaml or requirements.yaml                ====="
  echo "==================================================================="
  exit 1
fi
