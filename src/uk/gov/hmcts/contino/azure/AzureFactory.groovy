package uk.gov.hmcts.contino.azure

@Grab('com.microsoft.azure:azure:1.10.0')
@GrabExclude('javax.mail:mail')

import com.microsoft.azure.credentials.AzureCliCredentials
import com.microsoft.azure.management.Azure

import java.nio.file.Paths

class AzureFactory {

  def steps

  AzureFactory(steps) {
    this.steps = steps
  }

  def getAzure(String subscription) {
    def azureProfile = Paths.get("/opt/jenkins/.azure-$subscription", "azureProfile.json").toFile()
    def accessTokens = Paths.get("/opt/jenkins/.azure-$subscription", "accessTokens.json").toFile()
    steps.echo("azureProfile.json exists ${azureProfile.exists()}")
    steps.echo("accessTokens.json exists ${accessTokens.exists()}")
    return Azure.authenticate(AzureCliCredentials.create(azureProfile, accessTokens)).withDefaultSubscription()
  }
}
