/*======================================================================================
dynatraceSyntheticTest

Runs Dynatrace synthetic browser tests and monitors their progress. This reads the
Dynatrace config from environment variables set by dynatracePerformanceSetup, so that
must run before this.

What it does:
  - Triggers a synthetic test in Dynatrace
  - Polls until the test finishes, checking the test status.
  - Records start/end times for SRG evaluation
  - Disables the synthetic test afterwards (preview environments only)
  - Waits 1 minute if Gatling tests are also running (to sync the start times)

@param params Map containing:
  - product: String product name (required, used for logging)
  - component: String component name (required, used for logging)
  - environment: String environment (required, used to detect preview)
  - performanceTestStagesEnabled: Boolean flag (required, used for sync check)
  - gatlingLoadTestsEnabled: Boolean flag (required, used for sync check)

Uses these environment variables:
  - DT_SYNTHETIC_TEST_ID: Which monitor to trigger (set by dynatracePerformanceSetup, origin is consuming services repo)
  - DT_DASHBOARD_URL: Dashboard URL (set by dynatracePerformanceSetup, origin is consuming services repo)
  - TEST_URL: Target URL (set by testEnv wrapper)

Sets these environment variables:
  - PERF_TEST_START_TIME: When the test started (Timestamp)
  - PERF_TEST_END_TIME: When the test finished (Timestamp)

Prerequisites:
  - dynatracePerformanceSetup must have run already
  - Vault secrets loaded (PERF_SYNTHETIC_MONITOR_TOKEN, PERF_SYNTHETIC_UPDATE_TOKEN)
  - TEST_URL must be set

Example:
dynatraceSyntheticTest([
  product: 'et',
  component: 'sya-api',
  environment: 'perftest',
  performanceTestStagesEnabled: true,
  gatlingLoadTestsEnabled: false
])

Related files:
  - Called by: sectionDeployToAKS.groovy and withPipeline.groovy
  - Requires: dynatracePerformanceSetup.groovy (must run first)
  - Runs alongside: gatlingExternalLoadTest.groovy (if both enabled)
  - May be followed by: evaluateDynatraceSRG.groovy
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

  def environment = params.environment
  def maxStatusChecks = 16
  def statusCheckInterval = 20

  def dynatraceClient = new DynatraceClient(this)

  echo "Starting Dynatrace synthetic test execution..."
  echo "Product: ${params.product}"
  echo "Component: ${params.component}"
  echo "Environment: ${environment}"
  echo "Test URL: ${env.TEST_URL}"

  echo "Using performance test secrets loaded from shared vault (already available as environment variables)..."

  try {

    echo "Triggering Dynatrace Synthetic Test..."
    echo "DT Host: ${DynatraceClient.DEFAULT_DYNATRACE_API_HOST}"
    echo "Trigger Endpoint: ${DynatraceClient.DEFAULT_TRIGGER_SYNTHETIC_ENDPOINT}"
    echo "Synthetic Test: ${env.DT_SYNTHETIC_TEST_ID}"
    echo "Dashboard: ${env.DT_DASHBOARD_URL}"
    echo "Environment: ${environment}"
    
    // Conditional synchronization - only if both performance tests are enabled
    def bothTestsEnabled = params.performanceTestStagesEnabled && params.gatlingLoadTestsEnabled
    echo "DEBUG: performanceTestStagesEnabled = ${params.performanceTestStagesEnabled}"
    echo "DEBUG: gatlingLoadTestsEnabled = ${params.gatlingLoadTestsEnabled}"
    echo "DEBUG: bothTestsEnabled = ${bothTestsEnabled}"
    
    if (bothTestsEnabled) {
      echo "Dynatrace: Both tests enabled - delaying execution by 1 minute to allow Gatling setup"
      sleep(time: 60, unit: "SECONDS")
      echo "Dynatrace: Starting synchronised test execution"
    } else {
      echo "Dynatrace: Single test execution (no sync needed)"
    }
    
    // Capture test start time for SRG evaluation
    def testStartTime = new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone('UTC'))
    env.PERF_TEST_START_TIME = testStartTime
    echo "Performance test start time: ${testStartTime}"
    
    //Trigger Synthetic Test
    def triggerResult = dynatraceClient.triggerSyntheticTest()

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

    // Capture test end time for SRG evaluation
    def testEndTime = new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone('UTC'))
    env.PERF_TEST_END_TIME = testEndTime
    echo "Performance test end time: ${testEndTime}"

    // Disable the synthetic once triggered for preview env
    if (env.TEST_URL && environment == 'preview') {
      echo "Disabling Dynatrace Synthetic Test for preview environment..."
      echo "Custom URL: ${env.TEST_URL}"
      def updateResult = dynatraceClient.updateSyntheticTest(
        false
      )
    }

  sleep(time:3600, unit: "SECONDS")  //******* Added temp sleep for preview deployment******

  } catch (Exception e) {
    echo "Error in Dynatrace synthetic test execution: ${e.message}"
    //currentBuild.result = 'UNSTABLE' * Do not currently fail build. Implement later once stabilisation complete
  }
}