#!/bin/bash

domain=$1
pfxPass=$2 #$(cat /dev/random | LC_CTYPE=C tr -dc "[:alpha:]" | head -c 8)
platform=$3
subscription=$4

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
commonName=core-compute-$platform
sed -i "s/commonNameVar/$commonName/g" $domain.conf


certjson=$(env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$subscription az keyvault certificate show --vault-name app-vault-${subscription} -n $domain)
if [[ $certjson = *"not found"* ]]; then
  echo "Cert doesn't exist... will create one"
  openssl req -new -sha256 -nodes -out $domain.csr -newkey rsa:2048 -keyout $domain.key -config <( cat $domain.conf )

  openssl x509 -req -in $domain.csr -signkey $domain.key -out $domain.cer

  openssl pkcs12 -export -in $domain.cer -inkey $domain.key -out $domain.pfx -password pass:$pfxPass

  rm -f $domain.key $domain.csr $domain.conf

  env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$subscription az keyvault certificate import --vault-name app-vault-${subscription} -n $domain -f $domain.pfx --password $pfxPass

  # get base64 representation of pfx file and write to file
  a=$(cat ./$domain.pfx)
  echo -n $a | base64 > base64.txt
  truncate -s -1 base64.txt
else
  echo "Cert found... Pulling pfxBlobString for existing one"
  env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$subscription az keyvault certificate show --vault-name app-vault-${subscription} --name $domain --query cer -o tsv > $domain.cer
  env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$subscription az keyvault certificate show --vault-name app-vault-${subscription} --name $domain --query x509ThumbprintHex -o tsv > base64.txt
fi

#to be put back in
echo "Pushing cert to app gateway..."
env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$subscription az network application-gateway auth-cert create --cert-file $domain.cer --gateway-name $domain --name $domain --resource-group core-infra-${platform}
rm -f $domain.cer



