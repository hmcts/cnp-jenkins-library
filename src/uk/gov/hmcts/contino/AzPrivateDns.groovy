package uk.gov.hmcts.contino

import uk.gov.hmcts.contino.azure.Az

class AzPrivateDns {

    def steps
    def environment
    def az
    def environmentDnsConfigEntry

    AzPrivateDns(steps, environment, environmentDnsConfigEntry) {
        this.steps = steps
        this.environment = environment
        this.az = new Az(this.steps, this.steps.env.SUBSCRIPTION_NAME)
        this.environmentDnsConfigEntry = environmentDnsConfigEntry
    }

   def getHostName(recordName) {
     def zone = this.environmentDnsConfigEntry.zone

     return "${recordName}.${zone}"
   }

   def checkForCname(recordName) {
    def active = this.environmentDnsConfigEntry.active
    if (!active) {
      this.steps.echo "Azure Private DNS registration not active for environment ${environment}"
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

    try {
      def cnameRecordSet = this.az.az "network private-dns record-set cname show -g ${resourceGroup} -z ${zone} -n ${recordName} --subscription ${subscription} -o tsv"
      return cnameRecordSet ? true : false
    } catch (e) {
      return false
    }

   }

   def registerDns(recordName, serviceIP) {
      if (!IPV4Validator.validate(serviceIP)) {
          throw new RuntimeException("Invalid IP address [${serviceIP}].")
      }

      def active = this.environmentDnsConfigEntry.active
      def subscription = this.environmentDnsConfigEntry.subscription
      def resourceGroup = this.environmentDnsConfigEntry.resourceGroup
      def ttl = this.environmentDnsConfigEntry.ttl
      def zone = this.environmentDnsConfigEntry.zone
      
      def aRecordSet

      // check for existing record
      try {
        aRecordSet = this.az.az "network private-dns record-set a show -g ${resourceGroup} -z ${zone} -n ${recordName} --subscription ${subscription} -o tsv"
      } catch (e) {
      } // do nothing, record not found
      
      if (!aRecordSet) {
        this.steps.echo "Registering DNS for ${recordName} to ${serviceIP} with ttl = ${ttl}"
        this.az.az "network private-dns record-set a create -g ${resourceGroup} -z ${zone} -n ${recordName} --ttl ${ttl} --subscription ${subscription}"
        this.az.az "network private-dns record-set a add-record -g ${resourceGroup} -z ${zone} -n ${recordName} -a ${serviceIP} --subscription ${subscription}"
      } else {
        this.steps.echo "Updating existing A record for ${recordName}"
        this.az.az "network private-dns record-set a update -g ${resourceGroup} -z ${zone} -n ${recordName} --subscription ${subscription} --set 'aRecords[0].ipv4Address=\"${serviceIP}\"' --set 'ttl=${ttl}'"
      }
    }
}
