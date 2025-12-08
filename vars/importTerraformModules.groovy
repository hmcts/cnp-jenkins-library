#!groovy

// Section to import Azure resources deployed using azurerm_template_deployment in to Terraform native resources

import groovy.json.JsonSlurperClassic

//can be run only inside withSubscription
def call(String subscription, String environment, String product, tags) {

    def jsonSlurper = new JsonSlurperClassic()

    String stateJsonString =  sh(script: "terraform show -json", returnStdout: true).trim()
    def stateJsonObj = jsonSlurper.parseText(stateJsonString)

    if (stateJsonObj.values != null) {
        def child_modules = stateJsonObj.values.root_module.child_modules

        // Get All resources to be imported
        def templateResources = child_modules.findAll { it.resources &&
                                                        it.resources[0].type == 'azurerm_template_deployment' &&
                                                        (it.resources[0].name == "namespace" ||
                                                        it.resources[0].name == "topic" ||
                                                        it.resources[0].name == "queue" ||
                                                        it.resources[0].name == "subscription") }

        if (templateResources.size() > 0) {
            stageWithAgent("Import Terraform Modules", product) {
                echo "Importing Terraform modules"

                Closure az = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$subscription az $cmd", returnStdout: true).trim() }

                def tfImport = "terraform import -var 'common_tags=${tags}' -var 'env=${environment}' -var 'product=${product}'" +
                                (fileExists("${environment}.tfvars") ? " -var-file=${environment}.tfvars" : "")

                // Backup state file
                def tfstate = "${product}/${environment}/terraform.tfstate"
                echo "Backup state file - ${tfstate}"
                def snapShot = az "storage blob snapshot --container-name=${env.STORE_sa_container_name_template}${environment} --name=${tfstate} --account-name=${env.STORE_sa_name_template}${subscription}"
                echo "State file backup of ${tfstate} completed"

                importModules = new ImportTerraformModules(this, environment, product, tags, az)
                importModules.initialise(tfImport)

                for (templateResource in templateResources.resources) {
                    for (resource in templateResource) {
                        if (resource.mode == "managed") {

                            // Service Bus Namespace
                            if (resource.name == "namespace") {
                                echo "Importing Service Bus Namespace - ${resource.values.name}"

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
                                echo "Importing Service Bus Queue - ${resource.values.name}"

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
                                echo "Importing Service Bus Topic - ${resource.values.name}"

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
                                echo "Importing Service Bus Subscription - ${resource.values.name}"

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
                echo "Import of Terraform modules completed"
            }
        }
    }
}

class ImportTerraformModules {
    def steps
    def environment
    def product
    def tags
    def tfImportCommand
    Closure az

    ImportTerraformModules(steps, environment, product, tags, azClosure) {
        this.steps = steps
        this.environment = environment
        this.product = product
        this.tags = tags
        this.az = azClosure
    }

    def initialise(String tfImport) {
        this.tfImportCommand = tfImport
    }

    // Method to import Service Bus Namespace
    def importServiceBusNamespaceModule(String serviceBusName, String resource_group_name, String module_reference) {
        try {
            String nsModule = module_reference + ".azurerm_servicebus_namespace.servicebus_namespace"
            String nsAuthRuleModule = module_reference + ".azurerm_servicebus_namespace_authorization_rule.send_listen_auth_rule"

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
