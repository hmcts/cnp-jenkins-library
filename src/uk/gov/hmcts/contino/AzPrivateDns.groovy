package uk.gov.hmcts.contino

import groovy.json.JsonOutput
import uk.gov.hmcts.contino.azure.Az

class AzPrivateDns {

    def steps
    def environment
    def subscription
    def az

    // environment -> [resource_group, subscription]
    private final Map<String, String[]> envToRgAndSubId = [
        "prod": ["mgmt-intdns-prod", "2b1afc19-5ca9-4796-a56f-574a58670244"],
        "idam-prod": ["mgmt-intdns-prod", "2b1afc19-5ca9-4796-a56f-574a58670244"],
        "aat": ["mgmt-intdns-nonprod", "b44eb479-9ae2-42e7-9c63-f3c599719b6f"],
        "demo": ["mgmt-intdns-nonprod", "b44eb479-9ae2-42e7-9c63-f3c599719b6f"],
        "ithc": ["mgmt-intdns-nonprod", "b44eb479-9ae2-42e7-9c63-f3c599719b6f"],
        "perftest": ["mgmt-intdns-nonprod", "b44eb479-9ae2-42e7-9c63-f3c599719b6f"],
        "preview": ["mgmt-intdns-nonprod", "b44eb479-9ae2-42e7-9c63-f3c599719b6f"],
        "saat": ["mgmt-intdns-nonprod", "b44eb479-9ae2-42e7-9c63-f3c599719b6f"],
        "sandbox": ["mgmt-intdns-sboxintsvc", "b3394340-6c9f-44ca-aa3e-9ff38bd1f9ac"],
        "idam-aat": ["mgmt-intdns-nonprod", "b44eb479-9ae2-42e7-9c63-f3c599719b6f"],
        "idam-demo": ["mgmt-intdns-nonprod", "b44eb479-9ae2-42e7-9c63-f3c599719b6f"],
        "idam-ithc": ["mgmt-intdns-nonprod", "b44eb479-9ae2-42e7-9c63-f3c599719b6f"],
        "idam-perftest": ["mgmt-intdns-nonprod", "b44eb479-9ae2-42e7-9c63-f3c599719b6f"],
        "idam-preview": ["mgmt-intdns-nonprod", "b44eb479-9ae2-42e7-9c63-f3c599719b6f"],
        "idam-saat": ["mgmt-intdns-nonprod", "b44eb479-9ae2-42e7-9c63-f3c599719b6f"],
        "idam-sandbox": ["mgmt-intdns-sboxintsvc", "b3394340-6c9f-44ca-aa3e-9ff38bd1f9ac"]
  ]

    AzPrivateDns(steps, environment) {
        this.steps = steps
        this.environment = environment
        this.subscription = this.steps.env.SUBSCRIPTION_NAME
        this.az = new Az(this.steps, this.subscription)
    }

    def resourceGroupName(environment) {
        return envToRgAndSubId[environment][0]
    }

    def dnsSubId(environment) {
        return envToRgAndSubId[environment][1]
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

    def getAccessToken() {
      def accessToken = this.az.az "account get-access-token --query accessToken -o tsv"
      if (!accessToken) {
        throw new RuntimeException("Failed to retrieve access token.")
      }
      return accessToken
    }

    def registerAzDns(recordName, serviceIP) {
        if (!IPV4Validator.validate(serviceIP)) {
            throw new RuntimeException("Invalid IP address [${serviceIP}].")
        }

        def subscriptionId = this.dnsSubId(environment)
        if (!subscriptionId) {
          throw new RuntimeException("No Subscription found for Environment [${environment}].")
        }

        def resourceGroup = this.resourceGroupName(environment)
        if (!resourceGroup) {
          throw new RuntimeException("No Resource Group found for Environment [${environment}].")
        }

        def ttl = this.ttl(environment)
        def zone = "service.core-compute-${environment}.internal"
        def json = JsonOutput.toJson(
            [
                "properties": [
                "ttl": "${ttl}",
                "aRecords": [["ipv4Address": "${serviceIP}"]]
                ],
        ])

        this.steps.echo "Registering DNS for ${recordName} to ${serviceIP}, properties: ${json}"

        def accessToken = getAccessToken()

        def req = this.steps.httpRequest(
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
