class AzPrivateDns {

    private subscriptionId = ""     // subscription id of azure private dns zones.
    private ResourceGroup = ""                     // resource group of azure dns zones
    private zone = ""
    private aRecordName = "${product}-${component}-${env}"

    // Login to determin which zone to deploy record to

    def RegisterAzDns(aRecord, serviceIP) {
    
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