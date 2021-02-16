#!groovy

import groovy.json.JsonSlurper
import uk.gov.hmcts.contino.azure.Az

//can be run only inside withSubscription
def call(String subscription, String environment, String product, tags) {
    echo "Importing Service Bus, Topic, Queue and Subscription modules"

    stageWithAgent("Import Service Bus Modules", product) {
        def jsonSlurper = new JsonSlurper()

        importModules = new ImportServiceBusModules(this, environment, product, tags)
        // az = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$subscription az $cmd", returnStdout: true).trim() }

        String stateJsonString =  sh(script: "terraform show -json", returnStdout: true).trim()
        def stateJsonObj = jsonSlurper.parseText(stateJsonString)

        // Get all modules
        def child_modules = stateJsonObj.values.root_module.child_modules

        for (module in child_modules) {
            def resources = module.resources

            for (resource in resources) {
                if (resource.type == "azurerm_template_deployment" && resource.name == "namespace") {
                    def address = resource.address.minus(".azurerm_template_deployment.namespace")
                    
                    echo "${address}"
                    echo "${resource.values.name}"
                    echo "${resource.values.resource_group_name}"

                    // if (importServiceBusNamespaceModule(resource.values.name, resource.values.resource_group_name, address, environment, product, tags)) {
                    if (importModules.importServiceBusNamespaceModule(resource.values.name, resource.values.resource_group_name, address)) {
                        echo "Import of Service Module - ${resource.values.name} is successful"
                    } else {
                        echo "Failed to import Serice Bus Module - ${resource.values.name}"
                        break
                    }
                }
            }
        }
    }
}

// def importServiceBusNamespaceModule(String serviceBusName, String resource_group_name, String module_reference, String environment, String product, pipelineTags) {
//     try {

//         echo "Importing Service Bus Namespace - ${serviceBusName}"

//         String nsModule = module_reference + ".azurerm_servicebus_namespace.servicebus_namespace"
//         String nsAuthRuleModule = module_reference + ".azurerm_servicebus_namespace_authorization_rule.servicebus_authorization_rule"

//         String serviceBusId = az "servicebus namespace show --name ${serviceBusName} --resource-group ${resource_group_name} --query id -o tsv"
//         String serviceBusAuthRuleID = az "servicebus namespace authorization-rule show --name SendAndListenSharedAccessKey --namespace-name ${serviceBusName} --resource-group ${resource_group_name} --query id -o tsv"

//         sh "terraform import -var 'common_tags=${pipelineTags}' -var 'env=${environment}' -var 'product=${product}'" +
//             (fileExists("${environment}.tfvars") ? " -var-file=${environment}.tfvars" : "") + " ${nsModule} ${serviceBusId}"

//         sh "terraform import -var 'common_tags=${pipelineTags}' -var 'env=${environment}' -var 'product=${product}'" +
//             (fileExists("${environment}.tfvars") ? " -var-file=${environment}.tfvars" : "") + " ${nsAuthRuleModule} ${serviceBusAuthRuleID}"

//         return true;
//     }
//     catch (err) {
//         echo err.getMessage()
//         return false;
//     }
// }

class ImportServiceBusModules {
    def steps
    def environment
    def product
    def tags
    def az

    ImportServiceBusModules(steps, environment, product, tags) {
        this.steps = steps
        this.environment = environment
        this.product = product
        this.tags = tags
        this.az = new Az(this.steps, this.steps.env.SUBSCRIPTION_NAME)
    }

    def importServiceBusNamespaceModule(String serviceBusName, String resource_group_name, String module_reference) {
        try {

            this.steps.echo "Importing Service Bus Namespace - ${serviceBusName}"

            String nsModule = module_reference + ".azurerm_servicebus_namespace.servicebus_namespace"
            String nsAuthRuleModule = module_reference + ".azurerm_servicebus_namespace_authorization_rule.servicebus_authorization_rule"

            String serviceBusId = this.az.az "servicebus namespace show --name ${serviceBusName} --resource-group ${resource_group_name} --query id -o tsv"
            String serviceBusAuthRuleID = this.az.az "servicebus namespace authorization-rule show --name SendAndListenSharedAccessKey --namespace-name ${serviceBusName} --resource-group ${resource_group_name} --query id -o tsv"

            this.steps.sh "terraform import -var 'common_tags=${this.tags}' -var 'env=${this.environment}' -var 'product=${this.product}'" +
                (fileExists("${this.environment}.tfvars") ? " -var-file=${this.environment}.tfvars" : "") + " ${nsModule} ${serviceBusId}"

            this.steps.sh "terraform import -var 'common_tags=${this.tags}' -var 'env=${this.environment}' -var 'product=${this.product}'" +
                (fileExists("${this.environment}.tfvars") ? " -var-file=${this.environment}.tfvars" : "") + " ${nsAuthRuleModule} ${serviceBusAuthRuleID}"

            return true;
        }
        catch (err) {
            this.steps.echo err.getMessage()
            return false;
        }
    }
}