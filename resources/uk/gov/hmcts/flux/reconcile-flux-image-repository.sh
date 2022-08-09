#!/bin/bash
# set -x

CHART_DIRECTORY=${1}-${2}
# CHART_DIRECTORY="plum-frontend"

if [[ $CHART_DIRECTORY = "plum-frontend" ]]; then
    CHART_DIRECTORY="cnp-plum-frontend"
fi

echo "Checking for flux image repository"

GET_FLUX_IMAGE_REPOSITORY=$(flux get image repository ${CHART_DIRECTORY})

if [[ -z $GET_FLUX_IMAGE_REPOSITORY ]]; then
    echo "No image repository found called ${CHART_DIRECTORY}. Are you sure this is the correct image repository name?"
    else
    echo "flux will now reconcile the image repository ${CHART_DIRECTORY}"
fi
