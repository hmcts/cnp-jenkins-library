#!/bin/bash

output=$(grep -R --include=*.tf "hmcts/cnp-module-redis" | wc -l)

if [ $(echo $output | tr -d " ") = 0 ]; then
  echo "Not using cnp-module-redis module"
else
  echo "====================================================================================================="
  echo "=== You appear to be using the cnp-module-redis terraform module ==="
  echo "=== As part of cost optimization, we are changing the default sku from Premium to Basic ==="
  echo "=== Please follow instruction in the readme file ==="
  echo "=== https://github.com/hmcts/cnp-module-redis/blob/mokainos-patch-1/README.md ===" 
  echo "=== This pipeline will start failing from 11th November 2023 ==="
  exit 1
fi