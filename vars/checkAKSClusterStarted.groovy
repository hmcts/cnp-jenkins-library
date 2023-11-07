import uk.gov.hmcts.contino.GithubAPI
import uk.gov.hmcts.contino.HealthChecker
import groovy.json.JsonSlurper

def call(Map params) {
  def environment = params.environment
  def subscription = params.subscription  
  def subscriptionName = params.autoStartSubscription.name
  def businessArea = env.BUSINESS_AREA_TAG
  def clusterResourceGroup = env.AKS_RESOURCE_GROUP
  def clusterName = env.AKS_CLUSTER_NAME


  log.info("Checking AKS Environment: $environment, Subscription is $subscriptionName, in business area: $businessArea")
  def azCommand = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-jenkins az $cmd", returnStdout: true).trim() }
  azCommand 'login --identity > /dev/null'
  azCommand "account set -s $subscriptionName"

  // Initial check to see if env is up by checking cluster status
  def clusterData = azCommand "aks show -n ${clusterName} -g ${clusterResourceGroup} -o json"
  def clusterStatus = new JsonSlurper().parseText(clusterData).powerState.code

  // Extract cluster number 
  def clusterNumber = clusterName[-6..-5]
  // Workflow only accepts sbox as a parameter
  environment = environment.replace("sandbox", "sbox")    

  if( clusterStatus == "Running" ){
    println "Cluster is running, continue pipeline"
  } else {
    println "AKS Cluster in stopped state - starting environment"
    GithubAPI gitHubAPI = new GithubAPI(this)
    gitHubAPI.startAksEnvironmentWorkflow("manual-start.yaml", "${businessArea}", "${clusterNumber}", "${environment}")
    def sleepDuration = 30
    def maxAttempts = 20
    // Check over 10 minutes that this has started
    def waitingTimeMinutes = (sleepDuration*maxAttempts)/60
    log.info("Waiting ${waitingTimeMinutes} minutes for AKS environment to be started...")
    def healthCheckUrl = "https://plum.sandbox.hmcts.net/health"
    def healthChecker = new HealthChecker(this)
    healthChecker.check(healthCheckUrl, sleepDuration, maxAttempts)
  }
}
