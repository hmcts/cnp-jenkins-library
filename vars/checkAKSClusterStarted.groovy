import uk.gov.hmcts.contino.GithubAPI
import groovy.json.JsonSlurper

def call(Map params) {
  def environment = params.environment
  def subscription = params.subscription  
  def subscription_name = params.aksSubscription.name

  def azCommand = { cmd -> return sh(script: "env AZURE_CONFIG_DIR=/opt/jenkins/.azure-jenkins az $cmd", returnStdout: true).trim() }
  def business_area = env.BUSINESS_AREA_TAG
  def cluster_rg = env.AKS_RESOURCE_GROUP
  def cluster_name = env.AKS_CLUSTER_NAME

  log.info("Checking AKS Environment: $environment, Subscription is $subscription_name, in business area: $business_area")
  azCommand 'login --identity > /dev/null'
  azCommand "account set -s $subscription_name"

  // Initial check to see if env is up by checking cluster status
  def cluster_data = azCommand "aks show -n ${cluster_name} -g ${cluster_rg} -o json"
  def cluster_status = new JsonSlurper().parseText(cluster_data).powerState.code
  // Extract cluster number 
  def cluster_number = cluster_name[-6..-5]
  // Workflow only accepts sbox as a parameter
  environment = environment.replace("sandbox", "sbox")    

  if( cluster_status == "Running" ){
    println "Cluster is running, continue pipeline"
    GithubAPI gitHubAPI = new GithubAPI(this)
    gitHubAPI.startAksEnvironmentWorkflow("manual-start.yaml", "${business_area}", "${cluster_number}", "${environment}")
  } else {
    println "AKS Cluster in stopped state"
  }
}