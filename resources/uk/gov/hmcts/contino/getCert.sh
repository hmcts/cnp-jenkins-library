#!/bin/bash

environment=$1

# Download the cert from vault. At present there is no way to download the cert as a pfx using the cli.
# As a result of this, we must download as pem then do the conversion ourselves before assigning it to the resource

az keyvault certificate download --file $environment.pem --vault-name infra-vault-$environment --name $environment
PW=$(az keyvault secret show --vault-name infra-vault-$environment --name ${environment}CertPW --query value)
PW="${PW#\"}"
PW="${PW%\"}"
openssl pkcs12 -export -nokeys -in $environment.pem -export -out $environment.pfx -passout pass::$PW

# get base64 representation of pfx file and write to file
a=$(cat ./$environment.pfx)
echo -n $a | base64 > base64.txt
truncate -s -1 base64.txt

# get certificate thumbprint and write to file
az keyvault certificate show --vault-name infra-vault-$environment --name $environment --query x509ThumbprintHex --output tsv > thumbhex.txt

