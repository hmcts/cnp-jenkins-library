#!groovy

//can be run only inside withSubscription
def call(String subscription) {
    echo "Importing Service Bus, Topic and Subscription modules"

    String sbName = env.SERVICE_BUS_NAME
    String topicName = env.TOPIC_NAME
    String subName = env.SB_SUBSCRIPTION_NAME
    String rgName = env.RESOURCE_GROUP_NAME

    // Closure az = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$subscription az $cmd", returnStdout: true).trim() }
    
    // String serviceBusId = az "servicebus namespace show --name ${sbName} --resource-group ${rgName} --query id -o tsv"
    // String serviceBusAuthRuleID = az "servicebus namespace authorization-rule show --name SendAndListenSharedAccessKey --namespace-name ${sbName} --resource-group ${rgName} --query id -o tsv"

    // String topicId = az "servicebus topic show --name ${topicName} --namespace-name ${sbName} --resource-group ${rgName} --query id -o tsv"
    // String topicAuthRuleId = az "servicebus topic authorization-rule show --name SendAndListenSharedAccessKey --topic-name ${topicName} --namespace-name ${sbName} --resource-group ${rgName} --query id -o tsv"
    
    // String subscriptionId = az "servicebus topic subscription show --name ${subName} --namespace-name ${sbName} --topic-name ${topicName} --resource-group ${rgName} --query id -o tsv"

    // echo "SERVICE BUS ID - ${serviceBusId}"
    // echo "SERVICE BUS AUTH RULE ID - ${serviceBusAuthRuleID}"
    // echo "TOPIC ID - ${topicId}"
    // echo "TOPIC AUTH RULE ID - ${topicAuthRuleId}"
    // echo "SUBSCRIPTION ID - ${subscriptionId}"

    String nsIdentifier = "azurerm_template_deployment.namespace"
    String topicIdentifier = "azurerm_template_deployment.topic"
    String subIdentifier = "azurerm_template_deployment.subscription"

    String stateList = sh(script: "terraform state list", returnStdout: true).trim()

    String sbNamespaceModule = sh(script: "grep '$nsIdentifier' <<< '$stateList' | awk -F '$nsIdentifier' '{print \$1}'", returnStdout: true).trim()
    String topicModule = sh(script: "grep '$topicIdentifier' <<< '$stateList' | awk -F '$topicIdentifier' '{print \$1}'", returnStdout: true).trim()
    String subModule = sh(script: "grep '$subIdentifier' <<< '$stateList' | awk -F '$subIdentifier' '{print \$1}'", returnStdout: true).trim()

    String sbNamespaceModuleName = sbNamespaceModule + "azurerm_servicebus_namespace.servicebus_namespace"
    String sbNamespaceAuthModuleName = sbNamespaceModule + "azurerm_servicebus_namespace_authorization_rule.servicebus_authorization_rule"
    String topicModuleName = topicModule + "azurerm_servicebus_topic.servicebus_topic"
    String topicAuthModuleName = topicModule + "azurerm_servicebus_topic_authorization_rule.topic_authorization_rule"
    String subsciptionModuleName = subModule + "azurerm_servicebus_subscription.servicebus_subscription"

    echo "SERVICE BUS MODULE NAME - ${sbNamespaceModuleName}"
    echo "SERVICE BUS AUTH RULE - ${sbNamespaceAuthModuleName}"
    echo "TOPIC MODULE NAME - ${topicModuleName}"
    echo "TOPIC AUTH RULE - ${topicAuthModuleName}"
    echo "SUBSCTION MODULE NAME - ${subsciptionModuleName}"

    // Import Service Bus
    // sh "terraform import -var 'common_tags=${pipelineTags}' -var 'env=${environment}' -var 'product=${product}'" +
    //     (fileExists("${environment}.tfvars") ? " -var-file=${environment}.tfvars" : "") + "${sbNamespaceModuleName} ${serviceBusId}"

    // // Import Service Bus Authorisation Rule
    // sh "terraform import -var 'common_tags=${pipelineTags}' -var 'env=${environment}' -var 'product=${product}'" +
    //     (fileExists("${environment}.tfvars") ? " -var-file=${environment}.tfvars" : "") + "${sbNamespaceAuthModuleName} ${serviceBusAuthRuleID}"

    // // Import Service Bus Topic
    // sh "terraform import -var 'common_tags=${pipelineTags}' -var 'env=${environment}' -var 'product=${product}'" +
    //     (fileExists("${environment}.tfvars") ? " -var-file=${environment}.tfvars" : "") + "${topicModuleName} ${topicId}"

    // // Import Service Bus Topic Authorisation Rule
    // sh "terraform import -var 'common_tags=${pipelineTags}' -var 'env=${environment}' -var 'product=${product}'" +
    //     (fileExists("${environment}.tfvars") ? " -var-file=${environment}.tfvars" : "") + "${topicAuthModuleName} ${topicAuthRuleId}"

    // // Import Service Bus Subscription
    // sh "terraform import -var 'common_tags=${pipelineTags}' -var 'env=${environment}' -var 'product=${product}'" +
    //     (fileExists("${environment}.tfvars") ? " -var-file=${environment}.tfvars" : "") + "${subsciptionModuleName} ${subscriptionId}"

    sh "sudo apt-get install jq"

    sh "terraform show -json | jq -r '.values.root_module.child_modules[].resources[] | select(.address==\"module.servicebus-namespace.azurerm_template_deployment.namespace\") | .values.name'"
}