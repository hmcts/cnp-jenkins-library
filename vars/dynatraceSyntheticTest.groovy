/*======================================================================================
dynatraceSyntheticTest

Executes Dynatrace synthetic tests and monitors their execution.
This runs the actual synthetic test after setup has been completed by dynatracePerformanceSetup.

@param params Map containing:
  - product: String product name (required)
  - component: String component name (required)
  - environment: String environment name (required)
  - configPath: String path to performance config file (optional, defaults to 'src/test/performance/config/config.groovy')
  - testUrl: String test URL for the environment (optional, uses env.TEST_URL if not provided)
  - secrets: Map of vault secrets configuration (optional)

Prerequisites:
  - dynatracePerformanceSetup must be run first to set up environment variables
  - Same vault secrets as dynatracePerformanceSetup

Example usage:
dynatraceSyntheticTest([
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
    error("dynatraceSyntheticTest: 'product' parameter is required")
  }
  if (!params.component) {
    error("dynatraceSyntheticTest: 'component' parameter is required")
  }
  if (!params.environment) {
    error("dynatraceSyntheticTest: 'environment' parameter is required")
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
    
  } catch (Exception e) {
    echo "Error loading performance test configuration: ${e.message}"
    return
  }

  echo "Starting Dynatrace synthetic test execution..."
  echo "Product: ${params.product}"
  echo "Component: ${params.component}"
  echo "Environment: ${environment}"
  echo "Test URL: ${testUrl}"

  echo "Using performance test secrets loaded from shared vault (already available as environment variables)..."

  try {
    // Set DT params from config file
    def syntheticTestId = config.dynatraceSyntheticTest

    echo "Triggering Dynatrace Synthetic Test..."
    echo "DT Host: ${DynatraceClient.DEFAULT_DYNATRACE_API_HOST}"
    echo "Trigger Endpoint: ${DynatraceClient.DEFAULT_TRIGGER_SYNTHETIC_ENDPOINT}"
    echo "Synthetic Test: ${syntheticTestId}"
    echo "Dashboard: ${config.dynatraceDashboardURL}"
    echo "Environment: ${environment}"
    
    // Conditional synchronization - only if both performance tests are enabled
    def bothTestsEnabled = params.performanceTestStagesEnabled && params.gatlingLoadTestsEnabled
    echo "DEBUG: performanceTestStagesEnabled = ${params.performanceTestStagesEnabled}"
    echo "DEBUG: gatlingLoadTestsEnabled = ${params.gatlingLoadTestsEnabled}"
    echo "DEBUG: bothTestsEnabled = ${bothTestsEnabled}"
    
    if (bothTestsEnabled) {
      echo "Dynatrace: Setup complete, ready for synchronized execution"
      milestone(label: "dynatrace-ready", ordinal: 9000)
      echo "Dynatrace: Waiting for Gatling to be ready..."
      milestone(label: "both-perf-tests-ready", ordinal: 9001)
      echo "Dynatrace: Starting synchronized test execution NOW!"
    } else {
      echo "Dynatrace: Single test execution (no sync needed)"
    }
    
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
        echo "Dynatrace synthetic test completed successfully"
      } else if (status == "FAILED") {
        echo "Warning: Dynatrace synthetic test failed"
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

  } catch (Exception e) {
    echo "Error in Dynatrace synthetic test execution: ${e.message}"
    //currentBuild.result = 'UNSTABLE' * Do not currently fail build. Implement later once stabilisation complete
  }
}