package uk.gov.hmcts.contino

import uk.gov.hmcts.contino.azure.Az

class AzPublicDns {

    def steps
    def environment
    def az
    def environmentDnsConfigEntry
    
    AzPublicDns(steps,environment, environmentDnsConfigEntry) {
      this.steps = steps
      this.environment = environment
      this.az = new Az(this.steps, this.steps.env.SUBSCRIPTION_NAME)
      this.environmentDnsConfigEntry = environmentDnsConfigEntry
    }

   def getHostName(recordName) {
     def zone = this.environmentDnsConfigEntry.zone

     return "${recordName}.${zone}"
   }

    def registerDns(recordName, serviceIP) {
        if (!IPV4Validator.validate(serviceIP)) {
            throw new RuntimeException("Invalid IP address [${serviceIP}].")
        }

        def active = this.environmentDnsConfigEntry.active
        if (!active) {
          this.steps.echo "Azure Public DNS registration not active for environment ${environment}"
          return
        }
        def subscription = this.environmentDnsConfigEntry.subscription
        if (!subscription) {
          throw new RuntimeException("No Subscription found for Environment [${environment}].")
        }

        def resourceGroup = this.environmentDnsConfigEntry.resourceGroup
        if (!resourceGroup) {
          throw new RuntimeException("No Resource Group found for Environment [${environment}].")
        }

        def ttl = this.environmentDnsConfigEntry.ttl
        def zone = this.environmentDnsConfigEntry.zone

        this.steps.echo "Checking public DNS for ${recordName}"
        try {
          def cnameRecordSet = this.az.az "network dns record-set cname show -g reformmgmtrg -z ${zone} -n ${recordName} --subscription Reform-CFT-Mgmt -o tsv --query 'CNAMERecord'"
          return cnameRecordSet
        } catch (e) {
          return ""
        } // do nothing, record not found
    }

}
