#!/bin/bash
set -x

CHART_DIRECTORY=${1}-${2}
CHART_API_VERSION=$(cat charts/"${CHART_DIRECTORY}"/Chart.yaml | grep ^apiVersion | cut -d ':' -f 2 | sed -e 's/^[[:space:]]*//')

if [[ ${CHART_API_VERSION} == "v1" ]]; then
  echo "Chart Api version  ${CHART_API_VERSION} is not supported"
  exit 1
fi

if [[ -f charts/"${CHART_DIRECTORY}"/requirements.yaml ]]; then
  echo "requirements.yaml file is not supported"
  exit 1
fi
