package uk.gov.hmcts.contino

import groovy.json.JsonOutput
import uk.gov.hmcts.contino.azure.Az

class AzPrivateDns extends Az {

    def steps
    def environment
    private subscriptionId
    private resourceGroup
    
    AzPrivateDns(steps, subscription, environment) {
        super(steps, subscription)

        this.steps = steps
        this.environment = environment
    }

    def resourceGroupName(environment) {
        if (environment == "prod") {
            return "mgmt-intdns-prod"
        } else
        if (environment == "idam-prod") {
            return "mgmt-intdns-prod"
        } else
        if (environment == "aat") {
            return "mgmt-intdns-nonprod"
        } else       
        if (environment == "demo") {
            return "mgmt-intdns-nonprod"
        } else        
        if (environment == "ithc") {
            return "mgmt-intdns-nonprod"
        } else        
        if (environment == "perftest") {
            return "mgmt-intdns-nonprod"
        } else        
        if (environment == "preview") {
            return "mgmt-intdns-nonprod"
        } else        
        if (environment == "saat") {
            return "mgmt-intdns-nonprod"
        } else
        if (environment == "sandbox") {
            return "mgmt-intdns-sboxintsvc"
        } else       
        if (environment == "idam-aat") {
            return "mgmt-intdns-nonprod"
        } else       
        if (environment == "idam-demo") {
            return "mgmt-intdns-nonprod"
        } else        
        if (environment == "idam-ithc") {
            return "mgmt-intdns-nonprod"
        } else        
        if (environment == "idam-perftest") {
            return "mgmt-intdns-nonprod"
        } else       
        if (environment == "idam-preview") {
            return "mgmt-intdns-nonprod"
        } else        
        if (environment == "idam-saat") {
            return "mgmt-intdns-nonprod"
        } else
        if (environment == "idam-sandbox") {
            return "mgmt-intdns-sboxintsvc"
        } else {
            return "mgmt-intdns-prod"
        }  
    }

    def dnsSubId(environment) {
        if (environment == "prod") {
            return "2b1afc19-5ca9-4796-a56f-574a58670244"
        } else   
        if (environment == "idam-prod") {
            return "2b1afc19-5ca9-4796-a56f-574a58670244"
        } else
        if (environment == "aat") {
            return "b44eb479-9ae2-42e7-9c63-f3c599719b6f"
        } else   
        if (environment == "demo") {
            return "b44eb479-9ae2-42e7-9c63-f3c599719b6f"
        } else   
        if (environment == "ithc") {
            return "b44eb479-9ae2-42e7-9c63-f3c599719b6f"
        } else   
        if (environment == "perftest") {
            return "b44eb479-9ae2-42e7-9c63-f3c599719b6f"
        } else   
        if (environment == "preview") {
            return "b44eb479-9ae2-42e7-9c63-f3c599719b6f"
        } else    
        if (environment == "saat") {
            return "b44eb479-9ae2-42e7-9c63-f3c599719b6f"
        } else
        if (environment == "sandbox") {
            return "b3394340-6c9f-44ca-aa3e-9ff38bd1f9ac"
        } else    
        if (environment == "idam-aat") {
            return "b44eb479-9ae2-42e7-9c63-f3c599719b6f"
        } else     
        if (environment == "idam-demo") {
            return "b44eb479-9ae2-42e7-9c63-f3c599719b6f"
        } else     
        if (environment == "idam-ithc") {
            return "b44eb479-9ae2-42e7-9c63-f3c599719b6f"
        } else  
        if (environment == "idam-perftest") {
            return "b44eb479-9ae2-42e7-9c63-f3c599719b6f"
        } else       
        if (environment == "idam-preview") {
            return "b44eb479-9ae2-42e7-9c63-f3c599719b6f"
        } else        
        if (environment == "idam-saat") {
            return "b44eb479-9ae2-42e7-9c63-f3c599719b6f"
        } else
        if (environment == "idam-sandbox") {
            return "b3394340-6c9f-44ca-aa3e-9ff38bd1f9ac"  
        } else {
            return "2b1afc19-5ca9-4796-a56f-574a58670244"
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

        def resourceGroup = this.resourceGroupName(environment)

        def ttl = this.ttl(environment)

        def zone = "service.core-compute-${environment}.internal"

        def json = JsonOutput.toJson(
            [
                "properties": [
                "ttl": "${ttl}",
                "aRecords": [["ipv4Address": "${serviceIP}"]]
                ],
        ])

        // def jsoncname = JsonOutput.toJson(
        //     [
        //         "properties": [
        //         "ttl": "${ttl}",
        //         "cnameRecord": [["cname": "${serviceIP}"]]
        //         ],
        // ])

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