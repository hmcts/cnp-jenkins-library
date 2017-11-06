import uk.gov.hmcts.contino.*

def call(String appName) {
  echo "Running SSL certificate creation script"
  String pfxPass = org.apache.commons.lang.RandomStringUtils.random(9, true, true)
  domain=appName

  result = sh script: """
#!/bin/bash
set +xe

echo "Creating Self-Signed cert for $domain"


cat > ${domain}.conf <<-EOF
[req]
default_bits = 2048
prompt = no
default_md = sha256
req_extensions = req_ext
distinguished_name = dn
[ dn ]
C=GB
ST=LONDON
L=LONDON
O=MOJ
OU=Engineering
emailAddress=support@moj
CN = commonNameVar.service.internal
[ req_ext ]
subjectAltName = @alt_names
[ alt_names ]
DNS.1 = scm.commonNameVar.service.internal
EOF

pwd
ls
cat ${domain}.conf

sed -i "s/commonNameVar/${domain}/g" ${domain}.conf

cat ${domain}.conf
openssl req -new -sha256 -nodes -out ${domain}.csr -newkey rsa:2048 -keyout ${domain}.key -config < cat ./${domain}.conf

openssl x509 -req -in ${domain}.csr -signkey ${domain}.key -out ${domain}.cer

openssl pkcs12 -export -in ${domain}.cer -inkey ${domain}.key -out ${domain}.pfx -password pass:$pfxPass

rm -f ${domain}.key ${domain}.csr ${domain}.conf

az keyvault certificate import --vault-name infra-vault -n ${domain} -f ${domain}.pfx --password $pfxPass
"""
  //script: libraryResource('uk/gov/hmcts/contino/create-cert'), returnStatus: true
  echo "Script return status: $result"
}
