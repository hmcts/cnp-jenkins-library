#!/bin/bash
export LC_ALL=C.UTF-8
export LANG=C.UTF-8

echo ${TEST_URL}

whoami

/opt/zap/zap.sh -daemon -host 0.0.0.0 -port 8080 -config database.newsession=3 -config database.newsessionprompt=false -config api.disablekey=true \
-config scanner.attackOnStart=true -config view.mode=attack -config globalexcludeurl.url_list.url.regex='^https?:\/\/.*\/(?:.*login.*)+$' \
-config rules.cookie.ignorelist=_ga,_gid,_gat,dtCookie,dtLatC,dtPC,dtSa,rxVisitor,rxvt -config connection.dnsTtlSuccessfulQueries=-1 -config api.addrs.addr.name=.* \
-config api.addrs.addr.regex=true -quickurl ${TEST_URL} -quickout activescan.html

echo 'Changing owner from $(id -u):$(id -g) to $(id -u):$(id -u)'
chown -R $(id -u):$(id -u) activescan.html
curl --fail http://0.0.0.0:8080/OTHER/core/other/jsonreport/?formMethod=GET --output report.json
cp *.html functional-output/