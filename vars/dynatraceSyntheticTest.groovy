/*======================================================================================
dynatraceSyntheticTest

Runs Dynatrace synthetic tests (HTTP or BROWSER) and monitors their progress. This reads
the Dynatrace config from environment variables set by dynatracePerformanceSetup, so that
must run before this.

What it does:
  - Triggers multiple executions of the same synthetic test (7 for HTTP, 3 for BROWSER)
  - Triggers are spaced 65 seconds apart (Dynatrace rate limit)
  - Polls all executions until they complete (EXECUTED), then checks test results (SUCCESS/FAILED)
  - Reports final results (SUCCESS/FAILED counts)
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

  //SuccessFlags
  def testExecutionSuccess  = true 
  def failureReason = ""
  def testResultStatus = "SUCCESS"  // Track overall test result

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
    
    // Conditional synchronisation - only if both performance tests are enabled
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

    // Determine trigger count based on monitor type (HTTP tests complete quicker so trigger more of these vs browser tests)
    def monitorType = env.DT_SYNTHETIC_TEST_ID.startsWith("HTTP") ? "HTTP" : "BROWSER"
    def triggerCount = monitorType == "HTTP" ? 7 : 3
    def delaySeconds = 65 //Dynatrace limitation (60 seconds between the same synthetic test triggers)

    echo "Triggering ${triggerCount} ${monitorType} synthetic test executions with ${delaySeconds}s intervals"

    // Trigger multiple executions and collect IDs
    // This is so we can collect more datapoints per synthetic test run
    def executionIds = []

    for (int i = 1; i <= triggerCount; i++) {
      echo "Triggering execution ${i}/${triggerCount}..."

      //Trigger the synthetic test
      def triggerResult = dynatraceClient.triggerSyntheticTest(xxxxxx)

      if (triggerResult && triggerResult.lastExecutionId) {
        executionIds.add(triggerResult.lastExecutionId)
        echo "Execution ${i} triggered with ID: ${triggerResult.lastExecutionId}"

        if (i < triggerCount) {
          echo "Waiting ${delaySeconds} seconds before next trigger..."
          sleep delaySeconds
        }
      } else {
        echo "Warning: Failed to trigger execution ${i}"
      }
    }

    if (executionIds.isEmpty()) {
      echo "ERROR: Failed to trigger any synthetic test executions"
      testExecutionSuccess = false
      failureReason = "Failed to trigger any synthetic test executions"

      // Set failure status before returning
      catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE', message: failureReason) {
        error(failureReason)
      }
      return
    }

    // Track status of all executions
    def executionStatuses = [:]

    def checkCount = 1
    echo "Monitoring ${executionIds.size()} synthetic test executions..."

    while (checkCount <= maxStatusChecks) {
      echo "Status check ${checkCount}/${maxStatusChecks}"

      def allCompleted = true

      // Check status of all executions
      executionIds.each { executionId ->
        def statusResult = dynatraceClient.getSyntheticStatus(executionId)

        if (statusResult && statusResult.executionStatus) {
          def executionStage = statusResult.executionStatus
          def testStatus = statusResult.testStatus

          // Store combined status: if executed, use the test result status, otherwise use execution stage
          if (executionStage == "EXECUTED" && testStatus) {
            executionStatuses[executionId] = testStatus
            echo "Execution ${executionId}: ${executionStage} - Result: ${testStatus}"
          } else {
            executionStatuses[executionId] = executionStage
            echo "Execution ${executionId}: ${executionStage}"
          }

          if (executionStage == "TRIGGERED") {
            allCompleted = false
          }
        } else {
          echo "Warning: Failed to get status for execution ${executionId}"
          allCompleted = false
        }
      }

      if (allCompleted) {
        echo "All executions complete"
        break
      }

      sleep statusCheckInterval
      checkCount++
    }

    // Report detailed results, count values from the executionsStatuses map
    def successCount = executionStatuses.values().count { it == "SUCCESS" }
    def failedCount = executionStatuses.values().count { it == "FAILED" }
    def triggeredCount = executionStatuses.values().count { it == "TRIGGERED" }

    echo "Synthetic test execution summary:"
    echo "Monitor: ${env.DT_SYNTHETIC_TEST_ID} (${monitorType})"
    echo "Total executions: ${executionIds.size()}"
    echo "Results: ${successCount} SUCCESS, ${failedCount} FAILED, ${triggeredCount} STILL RUNNING"
    echo ""
    echo "Execution details:"

    executionIds.eachWithIndex { execId, index ->
      def status = executionStatuses[execId] ?: "UNKNOWN"
      echo "  ${index + 1}. ${execId} - ${status}"
    }

    if (checkCount > maxStatusChecks) {
      echo "Warning: Synthetic test status check timed out after ${maxStatusChecks} attempts"
    }

    if (successCount == executionIds.size()) {
      echo "All Dynatrace synthetic tests completed successfully"
      testResultStatus = "SUCCESS"
    } else if (failedCount > 0) {
        echo "Warning: ${failedCount} synthetic test(s) failed"
        testExecutionSuccess = false
        failureReason = "${failedCount} of ${executionIds.size()} synthetic test(s) failed"
        testResultStatus = "UNSTABLE"
    } else if (triggeredCount > 0) {
        echo "Warning: ${triggeredCount} synthetic test(s) still running at timeout"
    } else {
        echo "Warning: Unexpected test result state"
        testExecutionSuccess = false
        failureReason = "Unexpected test state - ${successCount} success, ${failedCount} failed, ${triggeredCount} running"
        testResultStatus = "UNSTABLE"
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
  //************************* Added temp sleep for preview deployment ****************************************
  //sleep(time:3600, unit: "SECONDS") 
  //**********************************************************************************************************

  } catch (Exception e) {
    echo "Error in Dynatrace synthetic test execution: ${e.message}"
    testExecutionSuccess = false
    failureReason = "Synthetic test execution error: ${e.message}"
    testResultStatus = "FAILURE"
  }
  // Final evaluation: Set stage status based on test results
  if (!testExecutionSuccess) {
    catchError(buildResult: 'SUCCESS', stageResult: testResultStatus, message: "Dynatrace Synthetic Tests ${testResultStatus}: ${failureReason}") {
      error(failureReason)
    }
  }
}