#!/bin/bash
set -x

CHART_DIRECTORY=${1}-${2}

echo "Checking for flux image repository"

GET_FLUX_IMAGE_REPOSITORY=$(flux get image repository ${CHART_DIRECTORY})

if [[ -z $GET_FLUX_IMAGE_REPOSITORY ]]; then
    echo "No image repository found called ${CHART_DIRECTORY}. Are you sure this is the correct image repository name?" | tee no-image-repo
else
    flux reconcile image repository ${CHART_DIRECTORY}
fi
