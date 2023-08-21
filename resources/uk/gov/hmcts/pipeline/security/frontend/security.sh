#!/bin/bash
export LC_ALL=C.UTF-8
export LANG=C.UTF-8

if [[ -z ${TEST_URL} ]]; then
  echo "TEST_URL environment variable is empty"
  exit 1
else
  echo ${TEST_URL}
fi

sudo /opt/zap/zap.sh -config database.newsession=3 -config database.newsessionprompt=false -config api.disablekey=true \
-config scanner.attackOnStart=true -config view.mode=attack -config globalexcludeurl.url_list.url.regex='^https?:\/\/.*\/(?:.*login.*)+$' \
-config rules.cookie.ignorelist=_ga,_gid,_gat,dtCookie,dtLatC,dtPC,dtSa,rxVisitor,rxvt -config connection.dnsTtlSuccessfulQueries=-1 -config api.addrs.addr.name=.* \
-config api.addrs.addr.regex=true -cmd -quickurl ${TEST_URL} -quickout ${WORKSPACE}/activescan.html

echo 'Changing owner from $(id -u):$(id -g) to $(id -u):$(id -u)'
chown -R $(id -u):$(id -u) ${WORKSPACE}/activescan.html
curl --fail http://0.0.0.0:1001/OTHER/core/other/jsonreport/?formMethod=GET --output report.json
cp *.html functional-output/