#!/bin/bash
set -x

CHART_DIRECTORY=${1}-${2}

echo "Checking for flux image repository"

kubectl config get-contexts

flux get image repository $CHART_DIRECTORY
