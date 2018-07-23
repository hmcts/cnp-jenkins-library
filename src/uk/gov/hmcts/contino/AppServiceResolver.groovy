package uk.gov.hmcts.contino

class AppServiceResolver {

  def steps

  AppServiceResolver(steps) {
    this.steps = steps
  }

  def getServiceHost(String product, String component, String env, boolean staging = false) {

    String productComponentEnv = product + "-" + component + "-" + env
    def az = { cmd -> return steps.sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-$steps.env.SUBSCRIPTION_NAME az $cmd", returnStdout: true).trim() }

    def serviceHost
    if (staging) {
      serviceHost = az "webapp deployment slot list -g ${productComponentEnv} -n ${productComponentEnv} --query [].defaultHostName -o tsv"
    } else {
      serviceHost = az "webapp list -g ${productComponentEnv} --query [].defaultHostName -o tsv"
    }

    return serviceHost
  }
}
