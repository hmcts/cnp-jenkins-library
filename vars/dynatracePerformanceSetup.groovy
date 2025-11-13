/*======================================================================================
dynatracePerformanceSetup

Sets up Dynatrace monitoring for performance tests by posting events and metrics.
This should run before the actual performance tests to log build information.

@param params Map containing:
  - product: String product name (required)
  - component: String component name (required) 
  - environment: String environment name (required)
  - configPath: String path to performance config file (optional, defaults to 'src/test/performance/config/config.groovy')
  - testUrl: String test URL for the environment (optional, uses env.TEST_URL if not provided)
  - secrets: Map of vault secrets configuration (optional)

Prerequisites:
  - Same as performanceTestStages: vault secrets loaded from global KV
  - Required environment variables: PERF_SYNTHETIC_MONITOR_TOKEN, PERF_METRICS_TOKEN, PERF_EVENT_TOKEN, PERF_SYNTHETIC_UPDATE_TOKEN

Example usage:
dynatracePerformanceSetup([
  product: 'et',
  component: 'sya-api',
  environment: 'perftest',
  testUrl: env.TEST_URL,
  secrets: secrets
])
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
  def testUrl = env.TEST_URL
  
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
  env.DT_SYNTHETIC_TEST_ID = syntheticTestId
  env.DT_DASHBOARD_ID = dashboardId
  env.DT_ENTITY_SELECTOR = entitySelector
  env.DT_DASHBOARD_URL = config.dynatraceDashboardURL

  echo "Starting Dynatrace performance setup..."
  echo "Product: ${params.product}"
  echo "Component: ${params.component}"
  echo "Environment: ${environment}"
  echo "Test URL: ${testUrl}"
  echo "Workspace: ${env.WORKSPACE}"
  echo "Branch: ${env.BRANCH_NAME}"
  echo "Build Number: ${env.BUILD_NUMBER}"
  echo "Date/Time: ${new Date().format('yyyy-MM-dd HH:mm:ss')}"

  echo "Using performance test secrets loaded from shared vault (already available as environment variables)..."

  try {
    // Set DT params from config file
    def syntheticTestId = config.dynatraceSyntheticTest
    def dashboardId = config.dynatraceDashboardId
    def entitySelector = config.dynatraceEntitySelector
    
    // Handle missing CHANGE_URL for rebuilds
    if (!env.CHANGE_URL) {
      env.CHANGE_URL = "No change URL, likely a rebuild. Refer to build URL..."
    }

    echo "Posting Dynatrace Event..."
    echo "DT Host: ${DynatraceClient.DEFAULT_DYNATRACE_API_HOST}"
    echo "Synthetic Test: ${syntheticTestId}"
    echo "Entity Selector: ${entitySelector}"
    echo "Dashboard: ${config.dynatraceDashboardURL}"
    
    //Post DT Event
    def postEventResult = dynatraceClient.postEvent(
      syntheticTestId,
      dashboardId,
      entitySelector,
      params.product,
      params.component
    )

    echo "Posting Dynatrace Metric..."
    echo "DT Host: ${DynatraceClient.DEFAULT_DYNATRACE_API_HOST}"
    echo "Metric Endpoint: ${DynatraceClient.DEFAULT_METRIC_INGEST_ENDPOINT}"
    echo "Metric Type: ${config.dynatraceMetricType}"
    echo "Metric Tag: ${config.dynatraceMetricTag}"
    echo "Environment: ${environment}"
    
    //Post DT Metric
    def postMetricResult = dynatraceClient.postMetric(
      config.dynatraceMetricType,
      config.dynatraceMetricTag,
      environment
    )

    // Update Synthetic Test for preview environment if needed
    if (testUrl && environment == 'preview') {
      echo "Updating Dynatrace Synthetic Test for preview environment..."
      echo "Custom URL: ${testUrl}"
      //Update Synthetic Test for Preview
      def updateResult = dynatraceClient.updateSyntheticTest(
        syntheticTestId,
        true,
        testUrl
      )
    }

    echo "Dynatrace performance setup completed successfully"

  } catch (Exception e) {
    echo "Error in Dynatrace performance setup: ${e.message}"
    //currentBuild.result = 'UNSTABLE' * Do not currently fail build. Implement later once stabilisation complete
  }
}