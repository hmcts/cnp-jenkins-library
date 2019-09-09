class AzPrivateDns {

    def steps
    private subscriptionId = "bf308a5c-0624-4334-8ff8-8dca9fd43783"     // Subscription id of azure dns zone
    private resourceGroup = "rdo-private-dns-sbox"                      // Resource group of azure dns zone
    
    AzPrivateDns (steps) {

        this.steps = steps
        this.product = product
        this.component = component
        this.environment = environment

    }

    def registerAzDns(product, component, environment, serviceIP) {

        def recordName = "${product}-${component}-${environment}"

        // Logic to determin which dns zone the ASE application should register it's DNS record with

        switch(environment) {
            case prod:
                def zone = "service.core-compute-prod.internal"
                break;
            case sprod:
                def zone = "service.core-compute-sprod.internal"
                break;
            case sandbox:
                def zone = "service.core-compute-sandbox.internal"
                break;
            case perftest:
                def zone = "service.core-compute-perftest.internal"
                break;
            case aat:
                def zone = "service.core-compute-aat.internal"
                break;
            case ithc:
                def zone = "service.core-compute-ithc.internal"
                break;
            case demo:
                def zone = "service.core-compute-demo.internal"
                break;
            case preview:
                def zone = "service.core-compute-preview.internal"
                break;
            case snlperf:
                def zone = "service.core-compute-snlperf.internal"
                break;
            case idam-prod:
                def zone = "service.core-compute-idam-prod.internal"
                break;
            case idam-sprod:
                def zone = "service.core-compute-idam-sprod.internal"
                break;
            case idam-sandbox:
                def zone = "service.core-compute-idam-sandbox.internal"
                break;
            case idam-aat:
                def zone = "service.core-compute-idam-aat.internal"
                break;
            case idam-saat:
                def zone = "service.core-compute-idam-saat.internal"
                break;
            case idam-preview:
                def zone = "service.core-compute-idam-preview.internal"
                break;
            case idam-ithc:
                def zone = "service.core-compute-idam-ithc.internal"
                break;
            case idam-demo:
                def zone = "service.core-compute-idam-demo.internal"
                break;
            case idam-perftest:
                def zone = "service.core-compute-idam-perftest.internal"
                break;
            default:
                println("environment variable did not match to a dns zone")
                break;
        }

        // JSON payload to send to Azure private DNS API.  serviceIP is the ASE load balancer IP.

        def json = JsonOutput.toJson(
            [
                "properties"    : {
                    "ttl"           : "3600",
                    "aRecords"      : {
                        "ipv4Address"   : "${serviceIP}",
                    }
                }
            ]
        )

        def result = this.steps.httpRequest(
            httpMode: 'PUT',
            acceptType: 'APPLICATION_JSON',
            contentType: 'APPLICATION_JSON',
            url: "https://management.azure.com/subscriptions/${subscriptionId}/resourceGroups/${resourceGroup}/providers/Microsoft.Network/defDnsZones/${zone}/A/${recordName}?api-version=2018-09-01",
            requestBody: "${json}",
            consoleLogResponseBody: true,
            validResponseCodes: '201'
        )

    }

}