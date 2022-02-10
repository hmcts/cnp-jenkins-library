#!/bin/bash
set -x

CHART_DIRECTORY=${1}-${2}
MIN_JAVA_VERSION="3.7.0"
MIN_NODEJS_VERSION="2.4.0"
MIN_JOB_VERSION="0.7.3"

JAVA_VERSION=$(helm dependency ls charts/${CHART_DIRECTORY}/ | grep "java" |awk '{ print $2}' | sed "s/~//g")
NODEJS_VERSION=$(helm dependency ls charts/${CHART_DIRECTORY}/ | grep "nodejs" |awk '{ print $2}' | sed "s/~//g")
JOB_VERSION=$(helm dependency ls charts/${CHART_DIRECTORY}/ | grep "job" |awk '{ print $2}' | sed "s/~//g")

if [[ -n $JAVA_VERSION ]] &&  [[ $JAVA_VERSION < $MIN_JAVA_VERSION ]]; then
    echo "Java chart version $JAVA_VERSION is deprecated, please upgrade"
    exit 1
fi

if [[ -n $NODEJS_VERSION ]] && [[ $NODEJS_VERSION < $MIN_NODEJS_VERSION ]]; then
    echo "Nodejs chart version $NODEJS_VERSION is deprecated, please upgrade"
    exit 1
fi

if [[ -n $JOB_VERSION ]] && [[ $JOB_VERSION < $MIN_JOB_VERSION ]]; then
    echo "Job chart version $MIN_JOB_VERSION is deprecated, please upgrade"
    exit 1
fi
