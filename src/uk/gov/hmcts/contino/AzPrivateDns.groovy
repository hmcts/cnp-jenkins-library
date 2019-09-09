class AzPrivateDns {

    private subscriptionId = ""     // subscription id of azure private dns zones.
    private ResourceGroup = ""                     // resource group of azure dns zones
    private zone = ""
    private aRecordName = "${product}-${component}-${env}"

    // Logic to determin which dns zone the application should register it's DNS record with

    switch(env) {
        case prod:
            private zone = "service.core-compute-prod.internal"
            break;
        case sprod:
            private zone = "service.core-compute-sprod.internal"
            break;
        case sandbox:
            private zone = "service.core-compute-sandbox.internal"
            break;
        case perftest:
            private zone = "service.core-compute-perftest.internal"
            break;
        case aat:
            private zone = "service.core-compute-aat.internal"
            break;
        case ithc:
            private zone = "service.core-compute-ithc.internal"
            break;
        case demo:
            private zone = "service.core-compute-demo.internal"
            break;
        case preview:
            private zone = "service.core-compute-preview.internal"
            break;
        case snlperf:
            private zone = "service.core-compute-snlperf.internal"
            break;
        case idam-prod:
            private zone = "service.core-compute-idam-prod.internal"
            break;
        case idam-sprod:
            private zone = "service.core-compute-idam-sprod.internal"
            break;
        case idam-sandbox:
            private zone = "service.core-compute-idam-sandbox.internal"
            break;
        case idam-aat:
            private zone = "service.core-compute-idam-aat.internal"
            break;
        case idam-saat:
            private zone = "service.core-compute-idam-saat.internal"
            break;
        case idam-preview:
            private zone = "service.core-compute-idam-preview.internal"
            break;
        case idam-ithc:
            private zone = "service.core-compute-idam-ithc.internal"
            break;
        case idam-demo:
            private zone = "service.core-compute-idam-demo.internal"
            break;
        case idam-perftest:
            private zone = "service.core-compute-idam-perftest.internal"
            break;
        default:
            println("environment variable did not match to a dns zone")
            break;
    }

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

        def result = this.steps.httpRequest(
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