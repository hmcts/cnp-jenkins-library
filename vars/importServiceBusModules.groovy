#!groovy

import groovy.json.JsonSlurper
import uk.gov.hmcts.contino.azure.Az

//can be run only inside withSubscription
def call(String subscription, String environment, String product, tags) {
    echo "Importing Service Bus, Topic, Queue and Subscription modules"

    stageWithAgent("Import Service Bus Modules", product) {
        def jsonSlurper = new JsonSlurper()

        importModules = new ImportServiceBusModules(this, environment, product, tags)

        String stateJsonString =  sh(script: "terraform show -json", returnStdout: true).trim()
        def stateJsonObj = jsonSlurper.parseText(stateJsonString)

        // Get all modules
        def child_modules = stateJsonObj.values.root_module.child_modules

        for (module in child_modules) {
            def resources = module.resources

            for (resource in resources) {
                if (resource.type == "azurerm_template_deployment" && resource.name == "namespace") {
                    def address = resource.address.minus(".azurerm_template_deployment.namespace")
                    
                    if (importModules.importServiceBusNamespaceModule(resource.values.name, resource.values.resource_group_name, address)) {
                        echo "Import of Service Bus Module - ${resource.values.name} is successful"
                    } else {
                        echo "Failed to import Service Bus Module - ${resource.values.name}"
                        break
                    }
                }

                if (resource.type == "azurerm_template_deployment" && resource.name == "queue") {
                    def address = resource.address.minus(".azurerm_template_deployment.queue")
                    
                    if (importModules.importServiceBusQueueModule(resource.values.name, resource.values.parameters.serviceBusNamespaceName, resource.values.resource_group_name, address)) {
                        echo "Import of Service Bus Queue Module - ${resource.values.name} is successful"
                    } else {
                        echo "Failed to import Service Bus Queue Module - ${resource.values.name}"
                        break
                    }
                }

                if (resource.type == "azurerm_template_deployment" && resource.name == "topic") {
                    def address = resource.address.minus(".azurerm_template_deployment.topic")
                    
                    if (importModules.importServiceBusTopicModule(resource.values.name, resource.values.parameters.serviceBusNamespaceName, resource.values.resource_group_name, address)) {
                        echo "Import of Service Bus Topic Module - ${resource.values.name} is successful"
                    } else {
                        echo "Failed to import Service Bus Topic Module - ${resource.values.name}"
                        break
                    }
                }

                if (resource.type == "azurerm_template_deployment" && resource.name == "subscription") {
                    def address = resource.address.minus(".azurerm_template_deployment.subscription")
                    
                    if (importModules.importServiceBusSubscriptionModule(resource.values.name, resource.values.parameters.serviceBusNamespaceName, resource.values.parameters.serviceBusTopicName, resource.values.resource_group_name, address)) {
                        echo "Import of Service Bus Subscription Module - ${resource.values.name} is successful"
                    } else {
                        echo "Failed to import Service Bus Subscription Module - ${resource.values.name}"
                        break
                    }
                }
            }
        }
    }
    echo "Completed import of Service Bus, Topic, Queue and Subscription modules"
}

class ImportServiceBusModules {
    def steps
    def environment
    def product
    def tags
    def az
    def tfImportCommand

    ImportServiceBusModules(steps, environment, product, tags) {
        this.steps = steps
        this.environment = environment
        this.product = product
        this.tags = tags
        this.az = new Az(this.steps, this.steps.env.SUBSCRIPTION_NAME)
        this.tfImportCommand = "terraform import -var 'common_tags=${this.tags}' -var 'env=${this.environment}' -var 'product=${this.product}'" + 
                                (this.steps.fileExists("${this.environment}.tfvars") ? " -var-file=${this.environment}.tfvars" : "")
    }

    def importServiceBusNamespaceModule(String serviceBusName, String resource_group_name, String module_reference) {
        try {
            this.steps.echo "Importing Service Bus Namespace - ${serviceBusName}"

            String nsModule = module_reference + ".azurerm_servicebus_namespace.servicebus_namespace"
            String nsAuthRuleModule = module_reference + ".azurerm_servicebus_namespace_authorization_rule.send_listen_auth_rule"

            String serviceBusId = this.az.az "servicebus namespace show --name ${serviceBusName} --resource-group ${resource_group_name} --query id -o tsv"
            String serviceBusAuthRuleID = this.az.az "servicebus namespace authorization-rule show --name SendAndListenSharedAccessKey --namespace-name ${serviceBusName} --resource-group ${resource_group_name} --query id -o tsv"

            this.steps.sh this.tfImportCommand + " ${nsModule} ${serviceBusId}"

            this.steps.sh this.tfImportCommand + " ${nsAuthRuleModule} ${serviceBusAuthRuleID}"

            return true;
        }
        catch (err) {
            this.steps.echo err.getMessage()
            return false;
        }
    }

    def importServiceBusQueueModule(String queueName, String serviceBusName, String resource_group_name, String module_reference) {
        try {
            this.steps.echo "Importing Service Bus Queue - ${queueName}"

            String queueModule = module_reference + ".azurerm_servicebus_queue.servicebus_queue"
            String queueSendAuthRuleModule = module_reference + ".azurerm_servicebus_queue_authorization_rule.send_auth_rule"
            String queueListenAuthRuleModule = module_reference + ".azurerm_servicebus_queue_authorization_rule.listen_auth_rule"

            String queueId = this.az.az "servicebus queue show --name ${queueName} --namespace-name ${serviceBusName} --resource-group ${resource_group_name} --query id -o tsv"
            String queueSendAuthRuleID = this.az.az "servicebus queue authorization-rule show --name SendSharedAccessKey --namespace-name ${serviceBusName} --queue-name ${queueName} --resource-group ${resource_group_name} --query id -o tsv"
            String queueListenAuthRuleID = this.az.az "servicebus queue authorization-rule show --name ListenSharedAccessKey --namespace-name ${serviceBusName} --queue-name ${queueName} --resource-group ${resource_group_name} --query id -o tsv"

            this.steps.sh this.tfImportCommand + " ${queueModule} ${queueId}"

            this.steps.sh this.tfImportCommand + " ${queueSendAuthRuleModule} ${queueSendAuthRuleID}"

            this.steps.sh this.tfImportCommand + " ${queueListenAuthRuleModule} ${queueListenAuthRuleID}"

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
            String topicAuthRuleModule = module_reference + ".azurerm_servicebus_topic_authorization_rule.send_listen_auth_rule"

            String topicId = this.az.az "servicebus topic show --name ${topicName} --namespace-name ${serviceBusName} --resource-group ${resource_group_name} --query id -o tsv"
            String topicAuthRuleID = this.az.az "servicebus topic authorization-rule show --name SendAndListenSharedAccessKey --namespace-name ${serviceBusName} --topic-name ${topicName} --resource-group ${resource_group_name} --query id -o tsv"

            this.steps.sh this.tfImportCommand + " ${topicModule} ${topicId}"

            this.steps.sh this.tfImportCommand + " ${topicAuthRuleModule} ${topicAuthRuleID}"

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

            this.steps.sh this.tfImportCommand + " ${subModule} ${subId}"

            return true;
        }
        catch (err) {
            this.steps.echo err.getMessage()
            return false;
        }
    }
}