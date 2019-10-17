package uk.gov.hmcts.contino

import groovy.json.JsonOutput
import uk.gov.hmcts.contino.azure.Az

class AzPrivateDns extends Az {

    def steps
    def environment
    private subscriptionId
    private resourceGroup = "mgmt-intdns-prod"                             // Resource group of azure dns zone
    
    AzPrivateDns(steps, subscription, environment) {
        super(steps, subscription)

        this.steps = steps
        this.environment = environment
    }

    def dnsSubId(environment) {
        if (environment == "prod") {
            return "b72ab7b7-723f-4b18-b6f6-03b0f2c6a1bb"
        } else
        
        if (environment == "idam-prod") {
            return "b72ab7b7-723f-4b18-b6f6-03b0f2c6a1bb"
        } else {
            return "b72ab7b7-723f-4b18-b6f6-03b0f2c6a1bb"
        }
    }

    def ttl(environment) {
        if (environment == "prod") {
            return "3600"
        } else if (environment == "idam-prod") {
            return "3600"
        } else {
            return "300"
        }
    }

    def registerAzDns(recordName, serviceIP) {

        def subscriptionId = this.dnsSubId(environment)

        def ttl = this.ttl(environment)

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
            validResponseCodes: '200:201'
        )

    }

}