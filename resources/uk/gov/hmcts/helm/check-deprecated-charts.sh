#!/bin/bash
set -x

CHART_DIRECTORY=${1}-${2}
declare -A deprecationMap

deprecationMap["java"]="4.0.1"
deprecationMap["nodejs"]="2.4.5"
deprecationMap["job"]="0.7.3"
deprecationMap["blobstorage"]="0.3.0"
deprecationMap["servicebus"]="0.4.0"
deprecationMap["ccd"]="8.0.17"
deprecationMap["elasticsearch"]="7.8.2"

for deprecation in "${!deprecationMap[@]}"
do
  CURRENT_VERSION=$(helm dependency ls charts/${CHART_DIRECTORY}/ | grep "^$deprecation " |awk '{ print $2}' | sed "s/~//g")
  if [[ -n $CURRENT_VERSION ]] &&  [[ $CURRENT_VERSION < ${deprecationMap[$deprecation]} ]]; then
      echo "$deprecation chart $CURRENT_VERSION is deprecated, please upgrade to at least ${deprecationMap[$deprecation]}"
      exit 1
  fi
done
