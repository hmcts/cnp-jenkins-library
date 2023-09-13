#!/bin/bash
export LC_ALL=C.UTF-8
export LANG=C.UTF-8

echo ${TEST_URL}

zap-full-scan.py -t ${TEST_URL} -P 1001 -l FAIL -r /zap/wrk/activescan.html -d -z "-config database.newsession=3 -config database.newsessionprompt=false -config api.disablekey=true -config scanner.attackOnStart=true -config view.mode=attack -config globalexcludeurl.url_list.url\(0\).regex='^https?:\/\/.*\/(?:.*login.*)+$' -config rules.cookie.ignorelist=_ga,_gid,_gat,dtCookie,dtLatC,dtPC,dtSa,rxVisitor,rxvt -config connection.dnsTtlSuccessfulQueries=-1 -config api.addrs.addr.name=.* -config api.addrs.addr.regex=true ${URL_EXCLUSIONS}"

echo 'Changing owner from $(id -u):$(id -g) to $(id -u):$(id -u)'
chown -R $(id -u):$(id -u) /zap/wrk/activescan.html
curl --fail http://0.0.0.0:1001/OTHER/core/other/jsonreport/?formMethod=GET --output /zap/wrk/report.json
mkdir -p /zap/wrk/functional-output
chmod a+wx /zap/wrk/functional-output
cp /zap/wrk/*.html /zap/wrk/functional-output/
cp /zap/wrk/report.json /zap/wrk/functional-output/
