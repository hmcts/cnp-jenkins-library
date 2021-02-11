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

    String sbNamespaceModuleName = sh(script: "echo '$stateList' | grep '$nsIdentifier' | awk -F '$nsIdentifier' '{print \$1}'", returnStdout: true).trim()
    // String topicModuleName = sh(script: "echo $stateList | grep '$topicIdentifier' | awk -F '$topicIdentifier' '{print \$1}'", returnStdout: true).trim()
    // String subsciptionModuleName = sh(script: "echo $stateList | grep '$subIdentifier' | awk -F '$subIdentifier' '{print \$1}'", returnStdout: true).trim()

    echo "SERVICE BUS MODULE NAME - ${sbNamespaceModuleName}"
    // echo "TOPIC MODULE NAME - ${topicModuleName}"
    // echo "SUBSCTION MODULE NAME - ${subsciptionModuleName}"
}