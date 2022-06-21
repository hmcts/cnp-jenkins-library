#!/bin/bash
set -x

CHART_DIRECTORY=${1}-${2}
MIN_JAVA_VERSION="3.7.2"
MIN_NODEJS_VERSION="2.4.1"
MIN_JOB_VERSION="0.7.3"
MIN_BLOBSTORAGE_VERSION="0.2.2"
MIN_SERVICEBUS_VERSION="0.3.3"

JAVA_VERSION=$(helm dependency ls charts/${CHART_DIRECTORY}/ | grep "java" |awk '{ print $2}' | sed "s/~//g")
NODEJS_VERSION=$(helm dependency ls charts/${CHART_DIRECTORY}/ | grep "nodejs" |awk '{ print $2}' | sed "s/~//g")
JOB_VERSION=$(helm dependency ls charts/${CHART_DIRECTORY}/ | grep "job" |awk '{ print $2}' | sed "s/~//g")
BLOBSTORAGE_VERSION=$(helm dependency ls charts/${CHART_DIRECTORY}/ | grep "blobstorage" |awk '{ print $2}' | sed "s/~//g")
SERVICEBUS_VERSION=$(helm dependency ls charts/${CHART_DIRECTORY}/ | grep "servicebus" |awk '{ print $2}' | sed "s/~//g")

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

if [[ -n $BLOBSTORAGE_VERSION ]] && [[ $BLOBSTORAGE_VERSION < $MIN_BLOBSTORAGE_VERSION ]]; then
    echo "Job chart version $BLOBSTORAGE_VERSION is deprecated, please upgrade"
    exit 1
fi

if [[ -n $STORAGEBUS_VERSION ]] && [[ $STORAGEBUS_VERSION < $MIN_STORAGEBUS_VERSION ]]; then
    echo "Job chart version $MIN_STORAGEBUS_VERSION is deprecated, please upgrade"
    exit 1
fi
