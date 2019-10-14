package uk.gov.hmcts.contino

import groovy.json.JsonOutput
import uk.gov.hmcts.contino.azure.Az

class AzPrivateDns extends Az {

    def steps
    def environment
    private resourceGroup = "mgmt-intdns-prod"                             // Resource group of azure dns zone

    if (environment == "prod") {
        private subscriptionId = "b72ab7b7-723f-4b18-b6f6-03b0f2c6a1bb"
    } else if (environment == "idam-prod") {
        private subscriptionId = "b72ab7b7-723f-4b18-b6f6-03b0f2c6a1bb"
    } else {
        private subscriptionId = "b72ab7b7-723f-4b18-b6f6-03b0f2c6a1bb"
    }
    
    AzPrivateDns(steps, subscription, environment) {
        super(steps, subscription)

        this.steps = steps
        this.environment = environment
    }

    def registerAzDns(recordName, serviceIP) {
        def zone = "service.core-compute-${environment}.internal"

        def json = JsonOutput.toJson(
            [
                "properties": [
                "ttl": 3600, 
                "aRecords": [["ipv4Address": serviceIP]]
                ],
        ])

        this.steps.echo "Registering DNS for ${recordName} to ${serviceIP}, properties: ${json}"
        def accessToken = this.az "account get-access-token --query accessToken -o tsv"

        def result = this.steps.httpRequest(
            httpMode: 'PUT',
            acceptType: 'APPLICATION_JSON',
            contentType: 'APPLICATION_JSON',
            url: "https://management.azure.com/subscriptions/${subscriptionId}/resourceGroups/${resourceGroup}/providers/Microsoft.Network/privateDnsZones/${zone}/A/${recordName}?api-version=2018-09-01",
            requestBody: "${json}",
            consoleLogResponseBody: true,
            customHeaders: [
                [maskValue: true, name: 'Authorization', value: "Bearer ${accessToken}"]
            ],
            validResponseCodes: '201'
        )

    }

}