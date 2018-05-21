package uk.gov.hmcts.contino.azure

@Grab('com.microsoft.azure:azure:1.10.0')
@GrabExclude('javax.mail:mail')

import com.microsoft.azure.credentials.ApplicationTokenCredentials
import com.microsoft.azure.management.Azure

class AzureFactory {

  def steps

  AzureFactory(steps) {
    this.steps = steps
  }

  def getAzure() {
    def clientId = steps.env.AZURE_CLIENT_ID
    def clientSecret = steps.env.AZURE_CLIENT_SECRET
    def tenantId = steps.env.AZURE_TENANT_ID
    def subscriptionId = steps.env.AZURE_SUBSCRIPTION_ID
    def creds = new ApplicationTokenCredentials(clientId, tenantId, clientSecret)
    return Azure.authenticate(creds).withSubscription(subscriptionId)
  }
}
