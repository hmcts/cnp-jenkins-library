import uk.gov.hmcts.contino.DynatraceClient

/*======================================================================================
performanceTestStages

 Executes performance testing stages with Dynatrace integration
 
 Common Dynatrace endpoints are provided by default - teams only need to configure
 their specific values (synthetic test IDs, dashboard IDs, etc.)
 
 @param params Map containing:
   - product: String product name (required) - Defined in consuming repo's jenkinsfile_CNP
   - component: String component name (required) - Defined in consuming repo's jenkinsfile_CNP
   - environment: String environment name (required) - Pulls from environment class
   - configPath: String path to performance config file (optional, defaults to 'src/test/performance/config/config.groovy')
   - testUrl: String test URL for the environment (optional, uses env.TEST_URL if not provided)
   - secrets: Map of vault secrets configuration (optional, used for documentation only - secrets should be loaded in main pipeline)
   
 Prerequisites:
   - Vault secrets must be loaded in the main pipeline using loadVaultSecrets()
   - Required environment variables: PERF_SYNTHETIC_MONITOR_TOKEN, PERF_METRICS_TOKEN, PERF_EVENT_TOKEN, PERF_SYNTHETIC_UPDATE_TOKEN

 Global defaults provided by DynatraceClient:
   - dynatraceApiHost: "https://yrk32651.live.dynatrace.com/"
   - dynatraceEventIngestEndpoint: "api/v2/events/ingest"
   - dynatraceMetricIngestEndpoint: "api/v2/metrics/ingest"
   - dynatraceTriggerSyntheticEndpoint: "api/v2/synthetic/executions/batch"
   - dynatraceUpdateSyntheticEndpoint: "api/v1/synthetic/monitors/"

 Example usage:
 performanceTestStages([
   product: 'et',
   component: 'sya-api', 
   environment: 'aat',
   testUrl: env.TEST_URL,
   secrets: secrets
 ])
 ============================================================================================*/


def call(Map params) {

  //Ensure required params are present
  if (!params.product) {
    error("performanceTestStages: 'product' parameter is required")
  }
  if (!params.component) {
    error("performanceTestStages: 'component' parameter is required")
  }
  if (!params.environment) {
    error("performanceTestStages: 'environment' parameter is required")
  }

  def defaultConfigPath = 'src/test/performance/config/config.groovy'
  def configPath = params.configPath ?: defaultConfigPath
  def environment = params.environment
  def testUrl = env.TEST_URL
  def maxStatusChecks = 16
  def statusCheckInterval = 20
  
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

  echo "Starting performance test execution..."
  echo "Product: ${params.product}"
  echo "Component: ${params.component}"
  echo "Environment: ${environment}"
  echo "Test URL: ${testUrl}"
  echo "Workspace: ${env.WORKSPACE}"
  echo "Branch: ${env.BRANCH_NAME}"
  echo "Build Number: ${env.BUILD_NUMBER}"
  echo "Date/Time: ${new Date().format('yyyy-MM-dd HH:mm:ss')}"

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

    echo "Triggering Dynatrace Synthetic Test..."
    echo "DT Host: ${DynatraceClient.DEFAULT_DYNATRACE_API_HOST}"
    echo "Trigger Endpoint: ${DynatraceClient.DEFAULT_TRIGGER_SYNTHETIC_ENDPOINT}"
    echo "Synthetic Test: ${syntheticTestId}"
    echo "Dashboard: ${config.dynatraceDashboardURL}"
    echo "Environment: ${environment}"
    
    //Trigger Synthetic Test
    def triggerResult = dynatraceClient.triggerSyntheticTest(
      syntheticTestId
    )

    if (!triggerResult || !triggerResult.lastExecutionId) {
      echo "Warning: Failed to trigger synthetic test or get execution ID"
      //currentBuild.result = 'UNSTABLE' ** Do not currently fail build. Additional logic required here once stablisation period complete **
      return
    }

    // Set vars for checking execution status
    def status = "TRIGGERED"
    def checkCount = 1
    def lastExecutionId = triggerResult.lastExecutionId
    
    echo "Monitoring synthetic test execution: ${lastExecutionId}"

    while (status == "TRIGGERED" && checkCount <= maxStatusChecks) {
      echo "Status check ${checkCount}/${maxStatusChecks} - Current status: ${status}"
      
      // Get synthetic test status
      def statusResult = dynatraceClient.getSyntheticStatus(
        lastExecutionId
      )
      
      if (statusResult && statusResult.executionStatus) {
        status = statusResult.executionStatus
        echo "Retrieved status: ${status}"
      } else {
        echo "Warning: Failed to get synthetic test status"
        break
      }
      
      if (status == "TRIGGERED") {
        sleep statusCheckInterval
        checkCount++
      }
    }

    if (checkCount > maxStatusChecks) {
      echo "Warning: Synthetic test status check timed out after ${maxStatusChecks} attempts"
      //currentBuild.result = 'UNSTABLE' ** Do not currently fail build. Additional logic required here once stablisation period complete **
    } else {
      echo "Final synthetic test status: ${status}"
      if (status == "SUCCESS") {
        echo "Performance test completed successfully"
      } else if (status == "FAILED") {
        echo "Warning: Performance test failed"
        //currentBuild.result = 'UNSTABLE' ** Do not currently fail build. Additional logic required here once stablisation period complete **
      }
    }

    // Disable the synthetic once triggered for preview env
    if (testUrl && environment == 'preview') {
      echo "Disabling Dynatrace Synthetic Test for preview environment..."
      echo "Custom URL: ${testUrl}"
      def updateResult = dynatraceClient.updateSyntheticTest(
        syntheticTestId,
        false,
        testUrl
      )
    }

  sleep(time:3600, unit: "SECONDS")  // Added temp sleep for preview deployment

  } catch (Exception e) {
    echo "Error in performance test execution: ${e.message}"
    //currentBuild.result = 'UNSTABLE' * Do not currently fail build. Implement later once stabilisation complete
  }
}