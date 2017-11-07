#!/usr/bin/env bash

pfxPass=$1


# Set our CSR variables
SUBJ="
C=UK
ST=
O=
localityName=LONDON
commonName=*.hmcts.net
organizationalUnitName=Engineering
emailAddress=support@moj
"


# Generate our Private Key, CSR and Certificate
openssl genrsa -out "waf.key" 2048
openssl req -new -subj "$(echo -n "$SUBJ" | tr "\n" "/")" -key "waf.key" -out "waf.csr" -passin pass:$pxfPass
openssl x509 -req -days 365 -in "waf.csr" -signkey "waf.key" -out "waf.crt"
openssl pkcs12 -export -out waf.pfx -inkey waf.key -in waf.crt -passout pass:$pxfPass
