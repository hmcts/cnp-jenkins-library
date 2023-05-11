#!/usr/bin/env bash
echo ${TEST_URL}

if [[ -z ${SecurityRules} ]]; then
  echo "SecurityRules variable not set. Please set it as a parameter in your Jenkinsfile_nightly file."
  exit 1
fi

zap-api-scan.py -t ${TEST_URL}/v2/api-docs -f openapi -S -d -u ${SecurityRules} -P 1001 -z "-config rules.cookie.ignorelist=_ga,_gid,_gat,dtCookie,dtLatC,dtPC,dtSa,rxVisitor,rxvt" -l FAIL
curl --fail http://0.0.0.0:1001/OTHER/core/other/jsonreport/?formMethod=GET --output report.json
export LC_ALL=C.UTF-8
export LANG=C.UTF-8
zap-cli --zap-url http://0.0.0.0 -p 1001 report -o /zap/api-report.html -f html
zap-cli --zap-url http://0.0.0.0 -p 1001 alerts -l Medium --exit-code False
mkdir -p functional-output
chmod a+wx functional-output
cp /zap/api-report.html functional-output/
cp *.* functional-output/
