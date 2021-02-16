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
                        echo "Import of Service Bus Module - ${resource.values.name} is successful"
                    } else {
                        echo "Failed to import Serice Bus Module - ${resource.values.name}"
                        break
                    }
                }

                if (resource.type == "azurerm_template_deployment" && resource.name == "topic") {
                    def address = resource.address.minus(".azurerm_template_deployment.topic")
                    
                    echo "${address}"
                    echo "${resource.values.name}"
                    echo "${resource.values.parameters.serviceBusNamespaceName}"
                    echo "${resource.values.resource_group_name}"

                    // if (importServiceBusNamespaceModule(resource.values.name, resource.values.resource_group_name, address, environment, product, tags)) {
                    if (importModules.importServiceBusTopicModule(resource.values.name, resource.values.parameters.serviceBusNamespaceName, resource.values.resource_group_name, address)) {
                        echo "Import of Service Bus Topic Module - ${resource.values.name} is successful"
                    } else {
                        echo "Failed to import Serice Bus Topic Module - ${resource.values.name}"
                        break
                    }
                }

                if (resource.type == "azurerm_template_deployment" && resource.name == "subscription") {
                    def address = resource.address.minus(".azurerm_template_deployment.subscription")
                    
                    echo "${address}"
                    echo "${resource.values.name}"
                    echo "${resource.values.parameters.serviceBusNamespaceName}"
                    echo "${resource.values.parameters.serviceBusTopicName}"
                    echo "${resource.values.resource_group_name}"

                    // if (importServiceBusNamespaceModule(resource.values.name, resource.values.resource_group_name, address, environment, product, tags)) {
                    if (importModules.importServiceBusSubscriptionModule(resource.values.name, resource.values.parameters.serviceBusNamespaceName, resource.values.parameters.serviceBusTopicName, resource.values.resource_group_name, address)) {
                        echo "Import of Service Bus Subscription Module - ${resource.values.name} is successful"
                    } else {
                        echo "Failed to import Serice Bus Subscription Module - ${resource.values.name}"
                        break
                    }
                }
            }
        }
    }
}

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
                (this.steps.fileExists("${this.environment}.tfvars") ? " -var-file=${this.environment}.tfvars" : "") + " ${nsModule} ${serviceBusId}"

            this.steps.sh "terraform import -var 'common_tags=${this.tags}' -var 'env=${this.environment}' -var 'product=${this.product}'" +
                (this.steps.fileExists("${this.environment}.tfvars") ? " -var-file=${this.environment}.tfvars" : "") + " ${nsAuthRuleModule} ${serviceBusAuthRuleID}"

            return true;
        }
        catch (err) {
            this.steps.echo err.getMessage()
            return false;
        }
    }

    def importServiceBusTopicModule(String topicName, String serviceBusName, String resource_group_name, String module_reference) {
        try {
            this.steps.echo "Importing Service Bus Topic - ${topicName}"

            String topicModule = module_reference + ".azurerm_servicebus_topic.servicebus_topic"
            String topicAuthRuleModule = module_reference + ".azurerm_servicebus_topic_authorization_rule.topic_authorization_rule"

            String topicId = this.az.az "servicebus topic show --name ${topicName} --namespace-name ${serviceBusName} --resource-group ${resource_group_name} --query id -o tsv"
            String topicAuthRuleID = this.az.az "servicebus topic authorization-rule show --name SendAndListenSharedAccessKey --namespace-name ${serviceBusName} --topic-name ${topicName} --resource-group ${resource_group_name} --query id -o tsv"

            this.steps.sh "terraform import -var 'common_tags=${this.tags}' -var 'env=${this.environment}' -var 'product=${this.product}'" +
                (this.steps.fileExists("${this.environment}.tfvars") ? " -var-file=${this.environment}.tfvars" : "") + " ${topicModule} ${topicId}"

            this.steps.sh "terraform import -var 'common_tags=${this.tags}' -var 'env=${this.environment}' -var 'product=${this.product}'" +
                (this.steps.fileExists("${this.environment}.tfvars") ? " -var-file=${this.environment}.tfvars" : "") + " ${topicAuthRuleModule} ${topicAuthRuleID}"

            return true;
        }
        catch (err) {
            this.steps.echo err.getMessage()
            return false;
        }
    }

    def importServiceBusSubscriptionModule(String subscriptionName, String serviceBusName, String topicName, String resource_group_name, String module_reference) {
        try {
            this.steps.echo "Importing Service Bus Subscription - ${subscriptionName}"

            String subModule = module_reference + ".azurerm_servicebus_subscription.servicebus_subscription"

            String subId = this.az.az "servicebus topic subscription show --name ${subscriptionName} --namespace-name ${serviceBusName} --topic-name ${topicName} --resource-group ${resource_group_name} --query id -o tsv"

            this.steps.sh "terraform import -var 'common_tags=${this.tags}' -var 'env=${this.environment}' -var 'product=${this.product}'" +
                (this.steps.fileExists("${this.environment}.tfvars") ? " -var-file=${this.environment}.tfvars" : "") + " ${subModule} ${subId}"

            return true;
        }
        catch (err) {
            this.steps.echo err.getMessage()
            return false;
        }
    }
}