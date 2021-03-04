#!groovy

import groovy.json.JsonSlurper
import uk.gov.hmcts.contino.azure.Az

//can be run only inside withSubscription
def call(String subscription, String environment, String product, tags) {
    echo "Importing Terraform modules"

    stageWithAgent("Import Terraform Modules", product) {
        def jsonSlurper = new JsonSlurper()

        def tfImport = "terraform import -var 'common_tags=${tags}' -var 'env=${environment}' -var 'product=${product}'" + 
                        (fileExists("${environment}.tfvars") ? " -var-file=${environment}.tfvars" : "")

        importModules = new ImportTerraformModules(this, environment, product, tags)
        importModules.initialise(tfImport)

        String stateJsonString =  sh(script: "terraform show -json", returnStdout: true).trim()
        def stateJsonObj = jsonSlurper.parseText(stateJsonString)

        // Create a backup/snapshot of the state file
        tfstate = "${product}/${environment}/terraform.tfstate"
        Closure az = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$subscription az $cmd", returnStdout: true).trim() }

        echo "Backup state file - ${tfstate}"
        def snapShot = az "storage blob snapshot --container-name=${env.STORE_sa_container_name_template}${environment} --name=${tfstate} --account-name=${env.STORE_sa_name_template}${subscription}"

        // Get all modules
        def child_modules = stateJsonObj.values.root_module.child_modules

        for (module in child_modules) {
            def resources = module.resources

            for (resource in resources) {
                if (resource.type == "azurerm_template_deployment") {

                    // Service Bus Namespace
                    if (resource.name == "namespace") {
                        echo "Importing Service Bus Namespace - ${resource.values.name}."

                        def address = resource.address.minus(".azurerm_template_deployment.namespace")
                        
                        if (importModules.importServiceBusNamespaceModule(resource.values.name, resource.values.resource_group_name, address)) {
                            echo "Import of Service Bus Namespace Module - ${resource.values.name} is successful."
                        } else {
                            echo "Failed to import Service Bus Namespace Module - ${resource.values.name}."
                            break
                        }
                    }

                    // Service Bus Queue
                    if (resource.name == "queue") {
                        echo "Importing Service Bus Queue - ${resource.values.name}."

                        def address = resource.address.minus(".azurerm_template_deployment.queue")
                        
                        if (importModules.importServiceBusQueueModule(resource.values.name, resource.values.parameters.serviceBusNamespaceName, resource.values.resource_group_name, address)) {
                            echo "Import of Service Bus Queue Module - ${resource.values.name} is successful."
                        } else {
                            echo "Failed to import Service Bus Queue Module - ${resource.values.name}."
                            break
                        }
                    }

                    // Service Bus Topic
                    if (resource.name == "topic") {
                        echo "Importing Service Bus Topic - ${resource.values.name}."

                        def address = resource.address.minus(".azurerm_template_deployment.topic")
                        
                        if (importModules.importServiceBusTopicModule(resource.values.name, resource.values.parameters.serviceBusNamespaceName, resource.values.resource_group_name, address)) {
                            echo "Import of Service Bus Topic Module - ${resource.values.name} is successful."
                        } else {
                            echo "Failed to import Service Bus Topic Module - ${resource.values.name}."
                            break
                        }
                    }

                    // Service Bus Subscription
                    if (resource.name == "subscription") {
                        echo "Importing Service Bus Subscription - ${resource.values.name}."

                        def address = resource.address.minus(".azurerm_template_deployment.subscription")
                        
                        if (importModules.importServiceBusSubscriptionModule(resource.values.name, resource.values.parameters.serviceBusNamespaceName, resource.values.parameters.serviceBusTopicName, resource.values.resource_group_name, address)) {
                            echo "Import of Service Bus Subscription Module - ${resource.values.name} is successful."
                        } else {
                            echo "Failed to import Service Bus Subscription Module - ${resource.values.name}."
                            break
                        }
                    }
                }
            }
        }
    }
    echo "Completed import of Terraform modules"
}

class ImportTerraformModules {
    def steps
    def environment
    def product
    def tags
    // def az
    Closure az
    def tfImportCommand
    

    ImportTerraformModules(steps, environment, product, tags) {
        this.steps = steps
        this.environment = environment
        this.product = product
        this.tags = tags
    }

    def initialise(String tfImport) {
        // this.az = new Az(this.steps, this.steps.env.SUBSCRIPTION_NAME)
        this.tfImportCommand = tfImport

        this.az = { cmd -> return this.steps.sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-${this.steps.env.SUBSCRIPTION_NAME} az $cmd", returnStdout: true).trim() }
    }

    // Method to import Service Bus Namespace
    def importServiceBusNamespaceModule(String serviceBusName, String resource_group_name, String module_reference) {
        try {
            String nsModule = module_reference + ".azurerm_servicebus_namespace.servicebus_namespace"
            String nsAuthRuleModule = module_reference + ".azurerm_servicebus_namespace_authorization_rule.send_listen_auth_rule"

            // String serviceBusId = this.az.az "servicebus namespace show --name ${serviceBusName} --resource-group ${resource_group_name} --query id -o tsv"
            // String serviceBusAuthRuleID = this.az.az "servicebus namespace authorization-rule show --name SendAndListenSharedAccessKey --namespace-name ${serviceBusName} --resource-group ${resource_group_name} --query id -o tsv"

            String serviceBusId = this.az "servicebus namespace show --name ${serviceBusName} --resource-group ${resource_group_name} --query id -o tsv"
            String serviceBusAuthRuleID = this.az "servicebus namespace authorization-rule show --name SendAndListenSharedAccessKey --namespace-name ${serviceBusName} --resource-group ${resource_group_name} --query id -o tsv"

            this.steps.sh "${this.tfImportCommand}" + " ${nsModule} ${serviceBusId}"

            this.steps.sh "${this.tfImportCommand}" + " ${nsAuthRuleModule} ${serviceBusAuthRuleID}"

            return true;
        }
        catch (err) {
            this.steps.echo err.getMessage()
            return false;
        }
    }

    // Method to import Service Bus Queue
    def importServiceBusQueueModule(String queueName, String serviceBusName, String resource_group_name, String module_reference) {
        try {
            String queueModule = module_reference + ".azurerm_servicebus_queue.servicebus_queue"
            String queueSendAuthRuleModule = module_reference + ".azurerm_servicebus_queue_authorization_rule.send_auth_rule"
            String queueListenAuthRuleModule = module_reference + ".azurerm_servicebus_queue_authorization_rule.listen_auth_rule"

            String queueId = this.az "servicebus queue show --name ${queueName} --namespace-name ${serviceBusName} --resource-group ${resource_group_name} --query id -o tsv"
            String queueSendAuthRuleID = this.az "servicebus queue authorization-rule show --name SendSharedAccessKey --namespace-name ${serviceBusName} --queue-name ${queueName} --resource-group ${resource_group_name} --query id -o tsv"
            String queueListenAuthRuleID = this.az "servicebus queue authorization-rule show --name ListenSharedAccessKey --namespace-name ${serviceBusName} --queue-name ${queueName} --resource-group ${resource_group_name} --query id -o tsv"

            this.steps.sh "${this.tfImportCommand}" + " ${queueModule} ${queueId}"

            this.steps.sh "${this.tfImportCommand}" + " ${queueSendAuthRuleModule} ${queueSendAuthRuleID}"

            this.steps.sh "${this.tfImportCommand}" + " ${queueListenAuthRuleModule} ${queueListenAuthRuleID}"

            return true;
        }
        catch (err) {
            this.steps.echo err.getMessage()
            return false;
        }
    }

    // Method to import Service Bus Topic
    def importServiceBusTopicModule(String topicName, String serviceBusName, String resource_group_name, String module_reference) {
        try {
            String topicModule = module_reference + ".azurerm_servicebus_topic.servicebus_topic"
            String topicAuthRuleModule = module_reference + ".azurerm_servicebus_topic_authorization_rule.send_listen_auth_rule"

            String topicId = this.az "servicebus topic show --name ${topicName} --namespace-name ${serviceBusName} --resource-group ${resource_group_name} --query id -o tsv"
            String topicAuthRuleID = this.az "servicebus topic authorization-rule show --name SendAndListenSharedAccessKey --namespace-name ${serviceBusName} --topic-name ${topicName} --resource-group ${resource_group_name} --query id -o tsv"

            this.steps.sh "${this.tfImportCommand}" + " ${topicModule} ${topicId}"

            this.steps.sh "${this.tfImportCommand}" + " ${topicAuthRuleModule} ${topicAuthRuleID}"

            return true;
        }
        catch (err) {
            this.steps.echo err.getMessage()
            return false;
        }
    }

    // Method to import Service Bus Subscription
    def importServiceBusSubscriptionModule(String subscriptionName, String serviceBusName, String topicName, String resource_group_name, String module_reference) {
        try {
            String subModule = module_reference + ".azurerm_servicebus_subscription.servicebus_subscription"

            String subId = this.az "servicebus topic subscription show --name ${subscriptionName} --namespace-name ${serviceBusName} --topic-name ${topicName} --resource-group ${resource_group_name} --query id -o tsv"

            this.steps.sh "${this.tfImportCommand}" + " ${subModule} ${subId}"

            return true;
        }
        catch (err) {
            this.steps.echo err.getMessage()
            return false;
        }
    }
}