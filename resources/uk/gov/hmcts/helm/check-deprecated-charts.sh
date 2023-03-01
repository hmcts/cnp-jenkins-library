#!/bin/bash
set -x

CHART_DIRECTORY=${1}-${2}
declare -A deprecationMap
function ver { printf "%03d%03d%03d%03d" $(echo "$1" | tr '.' ' '); }

deprecationMap["java"]="4.0.12"
deprecationMap["nodejs"]="2.4.14"
deprecationMap["job"]="0.7.10"
deprecationMap["blobstorage"]="0.3.0"
deprecationMap["servicebus"]="0.4.0"
deprecationMap["ccd"]="8.0.23"
deprecationMap["elasticsearch"]="7.17.3"

for deprecation in "${!deprecationMap[@]}"
do
  CURRENT_VERSION=$(helm dependency ls charts/${CHART_DIRECTORY}/ | grep "^$deprecation" |awk '{ print $2}' | sed "s/~//g")
  if [[ -n $CURRENT_VERSION ]] && [ $(ver $CURRENT_VERSION) -lt $(ver ${deprecationMap[$deprecation]}) ]; then
      echo "$deprecation chart $CURRENT_VERSION is deprecated, please upgrade to at least ${deprecationMap[$deprecation]}"
      exit 1
  fi
done
