package uk.gov.hmcts.contino.azure

import com.microsoft.azure.credentials.AzureCliCredentials
import com.microsoft.azure.management.Azure

import java.nio.file.Paths

@Grab('com.microsoft.azure:azure:1.10.0')

class AzureFactory {
  static getAzure(subscription) {
    def azureProfile = Paths.get("/opt/jenkins/.azure-$subscription", "azureProfile.json").toFile()
    def accessTokens = Paths.get("/opt/jenkins/.azure-$subscription", "accessTokens.json").toFile()
    return Azure.authenticate(AzureCliCredentials.create(azureProfile, accessTokens)).withDefaultSubscription()
  }
}
