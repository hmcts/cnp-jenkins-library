import uk.gov.hmcts.contino.DynatraceClient

/*======================================================================================
performanceTestStages

 Executes performance testing stages with Dynatrace integration
 
 Common Dynatrace endpoints are provided by default - teams only need to configure
 their specific values (synthetic test IDs, dashboard IDs, etc.)
 
 @param params Map containing:
   - product: String product name (required)
   - component: String component name (required) 
   - environment: String environment name (required)
   - configPath: String path to performance config file (optional, defaults to 'src/test/performance/config/config.groovy')
   - testUrl: String test URL for the environment (optional, uses env.TEST_URL if not provided)
   - secrets: Map of vault secrets configuration (optional, used for documentation only - secrets should be loaded in main pipeline)
   - syntheticTestId: String Dynatrace synthetic test ID (optional, overrides config)
   - dashboardId: String Dynatrace dashboard ID (optional, overrides config)
   - entitySelector: String Dynatrace entity selector (optional, overrides config)
   - maxStatusChecks: Integer maximum number of status checks (optional, defaults to 16)
   - statusCheckInterval: Integer seconds between status checks (optional, defaults to 20)
   
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
  def testUrl = params.testUrl ?: env.TEST_URL
  def maxStatusChecks = params.maxStatusChecks ?: 16
  def statusCheckInterval = params.statusCheckInterval ?: 20
  
  def config
  def dynatraceClient = new DynatraceClient(this)

  try {
    echo "Loading performance test configuration from: ${configPath}"
    config = load configPath
    
    if (!config) {
      error("Failed to load configuration from ${configPath}")
    }
    
    // Apply global defaults for common Dynatrace endpoints
    config.dynatraceApiHost = config.dynatraceApiHost ?: DynatraceClient.DEFAULT_DYNATRACE_API_HOST
    config.dynatraceEventIngestEndpoint = config.dynatraceEventIngestEndpoint ?: DynatraceClient.DEFAULT_EVENT_INGEST_ENDPOINT
    config.dynatraceMetricIngestEndpoint = config.dynatraceMetricIngestEndpoint ?: DynatraceClient.DEFAULT_METRIC_INGEST_ENDPOINT
    config.dynatraceTriggerSyntheticEndpoint = config.dynatraceTriggerSyntheticEndpoint ?: DynatraceClient.DEFAULT_TRIGGER_SYNTHETIC_ENDPOINT
    config.dynatraceUpdateSyntheticEndpoint = config.dynatraceUpdateSyntheticEndpoint ?: DynatraceClient.DEFAULT_UPDATE_SYNTHETIC_ENDPOINT
    
    config = DynatraceClient.setEnvironmentConfig(config, environment)
    
    echo "Configuration loaded successfully for environment: ${environment}"
    echo "API Host: ${config.dynatraceApiHost}"
    echo "Synthetic Test: ${config.dynatraceSyntheticTest}"
    echo "Dashboard: ${config.dynatraceDashboardURL}"
    
  } catch (Exception e) {
    echo "Error loading performance test configuration: ${e.message}"
    currentBuild.result = 'UNSTABLE'
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
    def syntheticTestId = params.syntheticTestId ?: config.dynatraceSyntheticTest
    def dashboardId = params.dashboardId ?: config.dynatraceDashboardId
    def entitySelector = params.entitySelector ?: config.dynatraceEntitySelector

    echo "Posting Dynatrace Event..."
    echo "DT Host: ${config.dynatraceApiHost}"
    echo "Synthetic Test: ${syntheticTestId}"
    echo "Entity Selector: ${entitySelector}"
    echo "Dashboard: ${config.dynatraceDashboardURL}"
    
    def postEventResult = dynatraceClient.postEvent(
      config.dynatraceApiHost,
      syntheticTestId,
      dashboardId,
      entitySelector,
      params.product,
      params.component
    )

    echo "Posting Dynatrace Metric..."
    echo "DT Host: ${config.dynatraceApiHost}"
    echo "Metric Endpoint: ${config.dynatraceMetricIngestEndpoint}"
    echo "Metric Type: ${config.dynatraceMetricType}"
    echo "Metric Tag: ${config.dynatraceMetricTag}"
    echo "Environment: ${environment}"
    
    try {
      def postMetricResult = dynatraceClient.postMetric(
        config.dynatraceApiHost,
        config.dynatraceMetricIngestEndpoint,
        config.dynatraceMetricType,
        config.dynatraceMetricTag,
        environment
      )
    } catch (Exception e) {
      echo "Warning: Failed to post Dynatrace metric: ${e.message}"
    }

    if (testUrl && environment == 'preview') {
      echo "Updating Dynatrace Synthetic Test for preview environment..."
      echo "Custom URL: ${testUrl}"
      try {
        def updateResult = dynatraceClient.updateSyntheticTest(
          config.dynatraceApiHost,
          config.dynatraceUpdateSyntheticEndpoint,
          syntheticTestId,
          true,
          testUrl
        )
      } catch (Exception e) {
        echo "Warning: Failed to update Dynatrace synthetic test: ${e.message}"
      }
    }

    echo "Triggering Dynatrace Synthetic Test..."
    echo "DT Host: ${config.dynatraceApiHost}"
    echo "Trigger Endpoint: ${config.dynatraceTriggerSyntheticEndpoint}"
    echo "Synthetic Test: ${syntheticTestId}"
    echo "Dashboard: ${config.dynatraceDashboardURL}"
    echo "Environment: ${environment}"
    
    def triggerResult = dynatraceClient.triggerSyntheticTest(
      config.dynatraceApiHost,
      config.dynatraceTriggerSyntheticEndpoint,
      syntheticTestId
    )

    if (!triggerResult || !triggerResult.lastExecutionId) {
      echo "Warning: Failed to trigger synthetic test or get execution ID"
      currentBuild.result = 'UNSTABLE'
      return
    }

    def status = "TRIGGERED"
    def checkCount = 1
    def lastExecutionId = triggerResult.lastExecutionId
    
    echo "Monitoring synthetic test execution: ${lastExecutionId}"

    while (status == "TRIGGERED" && checkCount <= maxStatusChecks) {
      echo "Status check ${checkCount}/${maxStatusChecks} - Current status: ${status}"
      
      try {
        def statusResult = dynatraceClient.getSyntheticStatus(
          config.dynatraceApiHost,
          lastExecutionId
        )
        
        if (statusResult && statusResult.executionStatus) {
          status = statusResult.executionStatus
          echo "Retrieved status: ${status}"
        } else {
          echo "Warning: Failed to get synthetic test status"
          break
        }
        
      } catch (Exception e) {
        echo "Warning: Error checking synthetic test status: ${e.message}"
        break
      }
      
      if (status == "TRIGGERED") {
        sleep statusCheckInterval
        checkCount++
      }
    }

    if (checkCount > maxStatusChecks) {
      echo "Warning: Synthetic test status check timed out after ${maxStatusChecks} attempts"
      currentBuild.result = 'UNSTABLE'
    } else {
      echo "Final synthetic test status: ${status}"
      if (status == "SUCCESS") {
        echo "Performance test completed successfully"
      } else if (status == "FAILED") {
        echo "Warning: Performance test failed"
        currentBuild.result = 'UNSTABLE'
      }
    }

  } catch (Exception e) {
    echo "Error in performance test execution: ${e.message}"
    currentBuild.result = 'UNSTABLE'
  }
}