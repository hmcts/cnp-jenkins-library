package uk.gov.hmcts.contino

import com.microsoft.azure.management.Azure
import uk.gov.hmcts.contino.azure.AzureFactory


class AppServiceResolver {

  def steps

  AppServiceResolver(steps) {
    this.steps = steps
  }

  def getServiceHost(String product, String component, String env, boolean staging = false) {

    String productComponentEnv = product + "-" + component + "-" + env
    def webApp = getAzure().webApps().getByResourceGroup(productComponentEnv, productComponentEnv)

    def serviceHost
    if (staging) {
      serviceHost = webApp.deploymentSlots().getByName("staging").defaultHostName()
    } else {
      serviceHost = webApp.defaultHostName()
    }

    return serviceHost
  }

  private Azure getAzure() {
    return (new AzureFactory()).getAzure(steps.env.SUBSCRIPTION_NAME)
  }
}
