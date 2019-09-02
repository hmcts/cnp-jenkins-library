class AzPrivateDns {

    private subscriptionId = "6aff0bdf-7c12-4850-8f7e-7dd4633b4110"     // subscription id of azure private dns zones. Reform-CFT-Sandbox
    private ResourceGroup = "gheath-test"                               // resource group of azure dns zones
    private zone = "service.core-compute-${env}.internal"
    private cnameRecord = "${product}-${component}-${env}"

    def RegisterAzDns(cnameRecord, cnameValue) {
    
        def json = JsonOutput.toJson(
            [
                "properties"    : {
                    "ttl"           : "3600"
                    "cnameRecord"   : {
                        "cname"         : cnameValue
                    }
                }
            ]
        )

        def res = this.steps.httpRequest(      // this needs to be tweeked
            httpMode: 'PUT',
            acceptType: 'APPLICATION_JSON',
            contentType: 'APPLICATION_JSON',
            url: "https://management.azure.com/subscriptions/${subscriptionId}/resourceGroups/${DnsResourceGroup}/providers/Microsoft.Network/privateDnsZones/${zone}/CNAME/${cnameRecord}?api-version=2018-09-01",
            requestBody: "${json}",
            consoleLogResponseBody: true,
            validResponseCodes: '201'
        )

    }

}

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