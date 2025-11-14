/*======================================================================================
dynatracePerformanceSetup

Sets up Dynatrace monitoring before running performance tests. This is the first stage
in the performance test pipeline and must run before dynatraceSyntheticTest.

What it does:
  - Loads the performance test config from your service repo (this requires services to onboard by adding the configuration method calls and DT config into their repository)
  - Stores Dynatrace settings as environment variables for later stages
  - Posts a build event to Dynatrace with metadata (branch, build number, etc.)
  - Sends a custom release metric to Dynatrace
  - Updates synthetic test URLs for preview environments

@param params Map containing:
  - product: String product name (required)
  - component: String component name (required)
  - environment: String environment (required) - 'perftest', 'aat', or 'preview'
  - configPath: String path to config file (optional, defaults to 'src/test/performance/config/config.groovy')

Sets these environment variables:
  - DT_SYNTHETIC_TEST_ID: The Dynatrace monitor ID
  - DT_DASHBOARD_ID: Dashboard ID for results
  - DT_ENTITY_SELECTOR: Entity selector for events
  - DT_DASHBOARD_URL: URL to view results
  - DT_METRIC_TYPE: Release metric type
  - DT_METRIC_TAG: Metric tag

Prerequisites:
  - Vault secrets must be loaded (done by sectionDeployToAKS):
    PERF_SYNTHETIC_MONITOR_TOKEN, PERF_METRICS_TOKEN, PERF_EVENT_TOKEN, PERF_SYNTHETIC_UPDATE_TOKEN
  - Your service repo must have a performance config file
  - TEST_URL must be set (done by testEnv wrapper)

Example:
dynatracePerformanceSetup([
  product: 'et',
  component: 'sya-api',
  environment: 'perftest'
])

Related files:
  - Called by: sectionDeployToAKS.groovy and withPipeline.groovy
  - Runs before: dynatraceSyntheticTest.groovy and/or gatlingExternalLoadTest.groovy
  - Config handler: DynatraceClient.setEnvironmentConfig()
============================================================================================*/

import uk.gov.hmcts.contino.DynatraceClient

def call(Map params) {

  //Ensure required params are present
  if (!params.product) {
    error("dynatracePerformanceSetup: 'product' parameter is required")
  }
  if (!params.component) {
    error("dynatracePerformanceSetup: 'component' parameter is required")
  }
  if (!params.environment) {
    error("dynatracePerformanceSetup: 'environment' parameter is required")
  }

  def defaultConfigPath = 'src/test/performance/config/config.groovy'
  def configPath = params.configPath ?: defaultConfigPath
  def environment = params.environment
   
  def config
  def dynatraceClient = new DynatraceClient(this)

  // Load config from consuming component repo
  try {
    echo "Loading performance test configuration from: ${configPath}"
    config = load configPath
    
    if (!config) {
      error("Failed to load configuration from ${configPath}")
    }
    
    //Load the correct config based on environment (switchCase function)
    config = DynatraceClient.setEnvironmentConfig(config, environment)
    
    echo "Configuration loaded successfully for environment: ${environment}"
    echo "API Host: ${DynatraceClient.DEFAULT_DYNATRACE_API_HOST}"
    echo "Synthetic Test: ${config.dynatraceSyntheticTest}"
    echo "Dashboard: ${config.dynatraceDashboardURL}"
    
  } catch (Exception e) {
    echo "Error loading performance test configuration: ${e.message}"
    //currentBuild.result = 'UNSTABLE' ** Do not currently fail build. Additional logic required here once stablisation period complete **
    return
  }

  echo "Setting config variables to env.VAR's..."
  // Store config for use by subsequent stages
  env.DT_SYNTHETIC_TEST_ID = config.dynatraceSyntheticTest
  env.DT_DASHBOARD_ID = config.dynatraceDashboardId
  env.DT_ENTITY_SELECTOR = config.dynatraceEntitySelector
  env.DT_DASHBOARD_URL = config.dynatraceDashboardURL
  env.DT_METRIC_TYPE = config.dynatraceMetricType
  env.DT_METRIC_TAG = config.dynatraceMetricTag

  echo "Starting Dynatrace performance setup..."
  echo "Product: ${params.product}"
  echo "Component: ${params.component}"
  echo "Environment: ${environment}"
  echo "Test URL: ${env.TEST_URL}"
  echo "Workspace: ${env.WORKSPACE}"
  echo "Branch: ${env.BRANCH_NAME}"
  echo "Build Number: ${env.BUILD_NUMBER}"
  echo "Date/Time: ${new Date().format('yyyy-MM-dd HH:mm:ss')}"

  echo "Using performance test secrets loaded from shared vault (already available as environment variables)..."

  try {
  
    // Handle missing CHANGE_URL for rebuilds
    if (!env.CHANGE_URL) {
      env.CHANGE_URL = "No change URL, likely a rebuild. Refer to build URL..."
    }

    echo "Posting Dynatrace Event..."
    echo "DT Host: ${DynatraceClient.DEFAULT_DYNATRACE_API_HOST}"
    echo "Synthetic Test: ${env.DT_SYNTHETIC_TEST_ID}"
    echo "Entity Selector: ${env.DT_ENTITY_SELECTOR}"
    echo "Dashboard: ${env.DT_DASHBOARD_URL}"
    
    //Post DT Event
    def postEventResult = dynatraceClient.postEvent(
      params.product,
      params.component
    )

    echo "Posting Dynatrace Metric..."
    echo "DT Host: ${DynatraceClient.DEFAULT_DYNATRACE_API_HOST}"
    echo "Metric Endpoint: ${DynatraceClient.DEFAULT_METRIC_INGEST_ENDPOINT}"
    echo "Metric Type: ${env.DT_METRIC_TYPE}"
    echo "Metric Tag: ${env.DT_METRIC_TAG}"
    echo "Environment: ${environment}"
    
    //Post DT Metric
    def postMetricResult = dynatraceClient.postMetric(
      environment
    )

    // Update Synthetic Test for preview environment if needed
    if (env.TEST_URL && environment == 'preview') {
      echo "Updating Dynatrace Synthetic Test for preview environment..."
      echo "Custom URL: ${env.TEST_URL}"
      //Update Synthetic Test for Preview
      def updateResult = dynatraceClient.updateSyntheticTest(
        true
      )
    }

    echo "Dynatrace performance setup completed successfully"

  } catch (Exception e) {
    echo "Error in Dynatrace performance setup: ${e.message}"
    //currentBuild.result = 'UNSTABLE' * Do not currently fail build. Implement later once stabilisation complete
  }
}