#!groovy

def call(productName, environment, subscription, status){
  withSubscription(subscription){

    if ( status == "on") {
      endpointStatus = "Enabled"
    }
    else {
      endpointStatus = "Disabled"
    }

    sh "az network traffic-manager endpoint update --resource-group ${productName}-${environment } --profile-name ${productName}-${environment } --name shutter -type externalEndpoints --endpoint-status ${endpointStatus}"

     }

  }
