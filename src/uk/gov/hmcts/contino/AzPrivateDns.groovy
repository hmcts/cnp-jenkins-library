package uk.gov.hmcts.contino

class AzPrivateDns {

    def steps
    def environment
    private subscriptionId = "bf308a5c-0624-4334-8ff8-8dca9fd43783"     // Subscription id of azure dns zone
    private resourceGroup = "rdo-private-dns-sbox"                      // Resource group of azure dns zone
    
    AzPrivateDns(steps, environment) {
        this.steps = steps
        this.environment = environment

    }

    def registerAzDns(recordName, serviceIP) {
        def zone = "service.core-compute-${environment}.internal"
        def json = "{ "properties" : { "ttl" : "3600", "aRecords" : [ { "ipv4Address" : "${serviceIP}" } ] } }" 

        this.steps.echo "Registering DNS for ${recordName} to ${serviceIP}, properties: ${json}"

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