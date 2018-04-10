#!groovy

def call(productName, environment, subscription, status){

    if ( status == "on") {
      endpointStatus = "Enabled"
    }
    else {
      endpointStatus = "Disabled"
    }

    withSubscription(subscription) {
      sh "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$subscription az network traffic-manager endpoint update --resource-group ${productName}-${environment} --profile-name ${productName}-${environment} --name shutter --type azureEndpoints --endpoint-status ${endpointStatus}"
    }

  }
