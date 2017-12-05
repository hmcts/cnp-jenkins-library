#!/bin/bash

domain=$1
pfxPass=$2 #$(cat /dev/random | LC_CTYPE=C tr -dc "[:alpha:]" | head -c 8)
platform=$3

echo "Creating Self-Signed cert for $domain"

echo "workspace for script = ${WORKSPACE}"
cat > $domain.conf <<-EOF
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
CN = *.service.commonNameVar.internal
[ req_ext ]
subjectAltName = @alt_names
[ alt_names ]
DNS.1 = *.scm.service.commonNameVar.internal

EOF

sed -i "s/commonNameVar/$domain/g" $domain.conf

openssl req -new -sha256 -nodes -out $domain.csr -newkey rsa:2048 -keyout $domain.key -config <( cat $domain.conf )

openssl x509 -req -in $domain.csr -signkey $domain.key -out $domain.cer

openssl pkcs12 -export -in $domain.cer -inkey $domain.key -out $domain.pfx -password pass:$pfxPass

rm -f $domain.key $domain.csr $domain.conf

#az keyvault certificate import --vault-name infra-vault -n $domain -f $domain.pfx --password $pfxPass

# whitelist app at appGw
#az network application-gateway auth-cert create --cert-file ./$domain.cer --gateway-name $domain --name $domain --resource-group $domain
az keyvault certificate import --vault-name app-vault -n $domain -f $domain.pfx --password $pfxPass

az network application-gateway auth-cert create --cert-file $domain.cer --gateway-name $domain --name $domain --resource-group core-infra-${platform}
