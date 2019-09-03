class AzPrivateDns {

    private subscriptionId = "6aff0bdf-7c12-4850-8f7e-7dd4633b411"     // subscription id of azure private dns zones. Reform-CFT-Sandbox
    private ResourceGroup = "rdo-private-dns-sbox"                     // resource group of azure dns zones
    private zone = "service.core-compute-${env}.internal"
    private aRecordName = "${product}-${component}-${env}"

    def RegisterAzDns(cnameRecord, serviceIP) {
    
        def json = JsonOutput.toJson(
            [
                "properties"    : {
                    "ttl"           : "3600"
                    "aRecords"      : {
                        "ipv4Address"   : "${serviceIP}"
                    }
                }
            ]
        )

        def result = this.steps.httpRequest(      // this needs to be tweeked
            httpMode: 'PUT',
            acceptType: 'APPLICATION_JSON',
            contentType: 'APPLICATION_JSON',
            url: "https://management.azure.com/subscriptions/${subscriptionId}/resourceGroups/${DnsResourceGroup}/providers/Microsoft.Network/privateDnsZones/${zone}/A/${aRecordName}?api-version=2018-09-01",
            requestBody: "${json}",
            consoleLogResponseBody: true,
            validResponseCodes: '201'
        )

    }

}

// https://management.azure.com/subscriptions/{subscriptionId}/resourceGroups/{resourceGroupName}/providers/Microsoft.Network/privateDnsZones/{privateZoneName}/{recordType}/{relativeRecordSetName}?api-version=2018-09-01

/*

https://plum-frontend-aat-staging.service.core-compute-aat.internal

REST API request:
PUT https://management.azure.com/subscriptions/subscriptionId/resourceGroups/resourceGroup1/providers/Microsoft.Network/privateDnsZones/privatezone1.com/CNAME/recordCNAME?api-version=2018-09-01

JSON payload:
{
  "properties": {
    "ttl": 3600,
    "cnameRecord": {
      "cname": "contoso.com"
    }
  }
}

*/