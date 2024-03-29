#!/bin/bash

output=$(grep -R --include=*.tf "hmcts/cnp-module-postgres" | wc -l)

if [ $(echo $output | tr -d " ") = 0 ]; then
  echo "Not using cnp-module-postgres module, this is good"
else
  echo "====================================================================================================="
  echo "=== You appear to be using the cnp-module-postgres terraform module ==="
  echo "=== This module has been deprecated as Postgres v11 went beyond end of life in Nov 2023 ===="
  echo "=== Please use the hmcts/terraform-module-postgresql-flexible module to create a flexible postgres server on v16 ==="
  echo "=== You will need to perform a migration of your data from the single server to the flexible server ==="
  echo "=== Follow the guidance on how to do this ==="
  echo "=== https://hmcts.github.io/cloud-native-platform/guides/postgresql-singleserver-to-flexibleserver-migration-portal.html ==="
  echo "=== https://hmcts.github.io/cloud-native-platform/guides/postgresql-singleserver-to-flexibleserver-migration-dms.html ==="
  echo "=== Once you have migrated your data, remove the cnp-module-postgres module from your code ==="
  echo "=== This pipeline will start failing from 31st January 2024 unless you have an approved exemption in https://github.com/hmcts/cnp-jenkins-library/blob/master/vars/warnAboutDeprecatedPostgres.groovy ==="
  exit 1
fi
