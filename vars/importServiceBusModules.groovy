#!groovy

import groovy.json.JsonSlurper

//can be run only inside withSubscription
def call(String subscription) {
    echo "Importing Service Bus, Topic and Subscription modules"

    def jsonSlurper = new JsonSlurper()
    def importModules = new ImportServiceBusModules(subscription, this)

    String stateJsonString =  sh(script: "terraform show -json", returnStdout: true).trim()

    def stateJsonObj = jsonSlurper.parseText(stateJsonString)

    // Get all modules
    def child_modules = stateJsonObj.values.root_module.child_modules

    for (module in child_modules) {
        def resources = module.resources
        
        for (resource in resources) {
            if (resource.type == "azurerm_template_deployment" && resource.name == "namespace") {
                def address = resource.address.minus(".azurerm_template_deployment.namespace")
                
                println (address)
                println (resource.values.name)
                println (resource.values.resource_group_name)

                if (importModules.ImportServiceBusNamespaceModule(resource.values.name, resource.values.resource_group_name, address)) {
                    echo "Import of Service Module - ${resource.values.name} is successful"
                } else {
                    echo "Failed to import Serice Bus Module - ${serviceBusName}"
                    break
                }
            }        
        }
    }
}

class ImportServiceBusModules {
    
    def steps
    String subscription = ""
    Closure az = { cmd -> return steps.sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$subscription az $cmd", returnStdout: true).trim() }

    ImportServiceBusModules(String current_subscription, steps) {
        subscription = current_subscription
        this.steps = steps
    }
    
    boolean ImportServiceBusNamespaceModule(String serviceBusName, String resource_group_name, String module_reference) {
        try {
            String nsModule = module_reference + ".azurerm_servicebus_namespace.servicebus_namespace"
            String nsAuthRuleModule = module_reference + ".azurerm_servicebus_namespace_authorization_rule.servicebus_authorization_rule"

            String serviceBusId = az "servicebus namespace show --name ${serviceBusName} --resource-group ${resource_group_name} --query id -o tsv"
            String serviceBusAuthRuleID = az "servicebus namespace authorization-rule show --name SendAndListenSharedAccessKey --namespace-name ${serviceBusName} --resource-group ${resource_group_name} --query id -o tsv"

            steps.echo "terraform import -var 'common_tags=${pipelineTags}' -var 'env=${environment}' -var 'product=${product}'" +
                (fileExists("${environment}.tfvars") ? " -var-file=${environment}.tfvars" : "") + nsModule + " " + serviceBusId

            steps.echo "terraform import -var 'common_tags=${pipelineTags}' -var 'env=${environment}' -var 'product=${product}'" +
                (fileExists("${environment}.tfvars") ? " -var-file=${environment}.tfvars" : "") + nsAuthRuleModule + " " + serviceBusAuthRuleID

            return true;
        }
        catch (err) {
            echo err.getMessage()
            return false;
        }
    }
}