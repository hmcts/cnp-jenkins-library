#!/bin/bash

domain=$1
pfxPass=$2 #$(cat /dev/random | LC_CTYPE=C tr -dc "[:alpha:]" | head -c 8)
gw=$3
rg=$3
ilb=$4

echo "Creating Self-Signed cert for $domain"


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
CN = commonNameVar.service.internal
[ req_ext ]
subjectAltName = @alt_names
[ alt_names ]
DNS.1 = scm.commonNameVar.service.internal
EOF

sed -i "s/commonNameVar/$domain/g" $domain.conf

openssl req -new -sha256 -nodes -out \*.$domain.csr -newkey rsa:2048 -keyout \*.$domain.key -config <( cat $domain.conf )

openssl x509 -req -in \*.$domain.csr -signkey \*.$domain.key -out $domain.cer

openssl pkcs12 -export -in $domain.cer -inkey \*.$domain.key -out $domain.pfx -password pass:$pfxPass

rm -f \*.$domain.key \*.$domain.csr $domain.conf

az keyvault certificate import --vault-name infra-vault -n $domain -f $domain.pfx --password $pfxPass

# whitelist app at appGw
az network application-gateway auth-cert create --cert-file ./$domain.cer --gateway-name $gw --name $domain --resource-group $rg

#get ip of consul
consul=$(az vmss nic list --resource-group $rg --vmss-name consul-server --query "[0].ipConfigurations[0].privateIpAddress")

# Create 2 consul entries (service + scm)
sed -i -e "s/serviceId/$domain/g" consul.json
sed -i -e "s/serviceName/$domain/g" consul.json
sed -i -e "s/aseIlb/$consul/g" consul.json

curl --request PUT --data @consul.json http://$consul:8500/v1/agent/service/register

sed -i -e "s/$domain/scm/g" consul.json
sed -i -e "s/\[\]/\[$domain\]/g" consul.json

curl --request PUT --data @consul.json http://$consul:8500/v1/agent/service/register
