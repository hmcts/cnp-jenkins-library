import uk.gov.hmcts.contino.GithubAPI
import uk.gov.hmcts.contino.HealthChecker
import groovy.json.JsonSlurperClassic

def call(Map params) {
  def environment = params.environment
  def subscription = params.subscription
  def subscriptionName = params.aksSubscription.name
  def businessArea = env.BUSINESS_AREA_TAG
  def clusterResourceGroup = env.AKS_RESOURCE_GROUP
  def clusterName = env.AKS_CLUSTER_NAME

  if(subscriptionName.toLowerCase().contains("sbox")){
    log.info("Checking AKS Environment: $environment, Subscription is $subscriptionName, in business area: $businessArea")
    def azCommand = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-jenkins az $cmd", returnStdout: true).trim() }
    azCommand "account set -s $subscriptionName"

    def clusterData = azCommand "aks show -n ${clusterName} -g ${clusterResourceGroup} -o json"
    def clusterStatus = new JsonSlurperClassic().parseText(clusterData).powerState.code
    def clusterNumber = clusterName[-6..-5]
    // Workflow only accepts sbox as a parameter
    environment = environment.replace("sandbox", "sbox")

    if(clusterStatus == "Running"){
      println "Cluster is running, continue pipeline"
    } else {
      println "AKS Cluster in stopped state - starting environment"
      GithubAPI gitHubAPI = new GithubAPI(this)
      gitHubAPI.startAksEnvironmentWorkflow("manual-start.yaml", "${businessArea}", "${clusterNumber}", "${environment}")
      def sleepDuration = 30
      def maxAttempts = 20
      def waitingTimeMinutes = (sleepDuration*maxAttempts)/60
      log.info("Waiting ${waitingTimeMinutes} minutes for AKS environment to be started...")
      def healthCheckUrl = "https://plum.sandbox.hmcts.net/health"
      def healthChecker = new HealthChecker(this)
      healthChecker.check(healthCheckUrl, sleepDuration, maxAttempts)
    }
  }
}
