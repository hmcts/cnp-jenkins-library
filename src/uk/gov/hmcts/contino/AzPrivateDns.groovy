package uk.gov.hmcts.contino

import uk.gov.hmcts.contino.azure.Az

class AzPrivateDns {

    def steps
    def environment
    def az
    def environmentDnsConfig

    AzPrivateDns(steps, environment, environmentDnsConfig) {
        this.steps = steps
        this.environment = environment
        this.az = new Az(this.steps, this.steps.env.SUBSCRIPTION_NAME)
        if (environmentDnsConfig != null) {
          this.environmentDnsConfig = environmentDnsConfig
        } else {
          this.environmentDnsConfig = new EnvironmentDnsConfig(steps)
        }
    }

    def registerDns(recordName, serviceIP) {
        if (!IPV4Validator.validate(serviceIP)) {
            throw new RuntimeException("Invalid IP address [${serviceIP}].")
        }

        def active = environmentDnsConfig.getEntry(environment).active
        if (!active) {
          this.steps.echo "Azure Private DNS registration not active for environment ${environment}"
          return
        }
        def subscription = environmentDnsConfig.getEntry(environment).subscription
        if (!subscription) {
          throw new RuntimeException("No Subscription found for Environment [${environment}].")
        }

        def resourceGroup = environmentDnsConfig.getEntry(environment).resourceGroup
        if (!resourceGroup) {
          throw new RuntimeException("No Resource Group found for Environment [${environment}].")
        }

        def ttl = this.environmentDnsConfig.getEntry(environment).ttl
        def zone = this.environmentDnsConfig.getEntry(environment).zone

        this.steps.echo "Registering DNS for ${recordName} to ${serviceIP} with ttl = ${ttl}"
        this.az.az "network private-dns record-set a create -g ${resourceGroup} -z ${zone} -n ${recordName} --ttl ${ttl} --subscription ${subscription}"
        this.az.az "network private-dns record-set a add-record -g ${resourceGroup} -z ${zone} -n ${recordName} -a ${serviceIP} --subscription ${subscription}"
    }

}
