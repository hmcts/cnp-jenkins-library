/*======================================================================================
evaluateDynatraceSRG

Checks performance test results against Site Reliability Guardian (SRG) quality gates
in Dynatrace. SRG lets you set thresholds for things like response times, error rates,
and resource usage. If the tests exceed those thresholds, this will fail or warn
depending on your config.

What it does:
  - Evaluates synthetic test results against SRG rules (if you ran synthetic tests)
  - Evaluates Gatling test results against SRG rules (if you ran Gatling tests)
  - Controls what happens when SRG checks fail (fail build, mark unstable, or just log)

You need to provide at least one pair of test times (either synthetic or Gatling).

@param params Map containing:
  - environment: String environment (required) - must match your SRG stage name
  - srgServiceName: String service name (required) - must match what's in Dynatrace exactly
  - performanceTestStartTime: String timestamp (optional) - usually from env.PERF_TEST_START_TIME
  - performanceTestEndTime: String timestamp (optional) - usually from env.PERF_TEST_END_TIME
  - gatlingTestStartTime: String timestamp (optional) - usually from env.GATLING_TEST_START_TIME
  - gatlingTestEndTime: String timestamp (optional) - usually from env.GATLING_TEST_END_TIME
  - srgFailureBehavior: String (optional, defaults to 'warn'):
    * 'fail' - Fails the pipeline
    * 'warn' - Marks build as unstable
    * 'ignore' - Just logs it
  - product: String product name (optional, for logging)
  - component: String component name (optional, for logging)

Uses these environment variables:
  - Usually reads PERF_TEST_START_TIME, PERF_TEST_END_TIME, GATLING_TEST_START_TIME, GATLING_TEST_END_TIME
    (these are passed in as parameters, but come from the environment)

Prerequisites:
  - Performance tests must have finished running
  - SRG must be set up in Dynatrace with rules for your service/stage
  - OAuth credentials in vault (ACCOUNT_URN, DYNATRACE_CLIENT_ID, DYNATRACE_SECRET)

Note: ** This isn't implemented yet ** The evaluation code is commented out in
DynatraceClient.groovy (lines 261-274) because we require a Dynatrace OAuth client to be setup
The structure is here ready for when that's sorted.

Example:
evaluateDynatraceSRG([
  environment: 'perftest',
  srgServiceName: 'et-sya-service',
  performanceTestStartTime: env.PERF_TEST_START_TIME,
  performanceTestEndTime: env.PERF_TEST_END_TIME,
  gatlingTestStartTime: env.GATLING_TEST_START_TIME,
  gatlingTestEndTime: env.GATLING_TEST_END_TIME,
  srgFailureBehavior: 'warn',
  product: 'et',
  component: 'sya-api'
])

Related files:
  - Called by: sectionDeployToAKS.groovy (only if you enable SRG evaluation)
  - Runs after: dynatraceSyntheticTest.groovy or gatlingExternalLoadTest.groovy
  - Implementation: DynatraceClient.evaluateSRG() (commented out for now)
============================================================================================*/

import uk.gov.hmcts.contino.DynatraceClient

def call(Map params) {
  
  // Validate required parameters
  if (!params.environment) {
    error("evaluateDynatraceSRG: 'environment' parameter is required")
  }
  if (!params.srgServiceName) {
    error("evaluateDynatraceSRG: 'srgServiceName' parameter is required - this must match the service name configured in Dynatrace SRG")
  }

  // Check that at least one test timing is provided
  if (!(params.performanceTestStartTime && params.performanceTestEndTime) && 
      !(params.gatlingTestStartTime && params.gatlingTestEndTime)) {
    error("evaluateDynatraceSRG: At least one set of test timing parameters is required")
  }

  def srgFailureBehavior = params.srgFailureBehavior ?: 'warn'
  def dynatraceClient = new DynatraceClient(this)
  def evaluationResults = []

  echo "Starting Site Reliability Guardian evaluations..."
  echo "Environment: ${params.environment}"
  echo "SRG Service Name: ${params.srgServiceName}"
  echo "Failure Behavior: ${srgFailureBehavior}"
  if (params.product) echo "Product: ${params.product}"
  if (params.component) echo "Component: ${params.component}"

  try {
    // Evaluate synthetic tests if timing provided
    if (params.performanceTestStartTime && params.performanceTestEndTime) {
      echo "Evaluating synthetic test performance..."
      
      def syntheticResult = dynatraceClient.evaluateSRG(
        params.srgServiceName,
        params.environment,
        params.performanceTestStartTime,
        params.performanceTestEndTime
      )
      
      evaluationResults.add([
        testType: 'Synthetic Tests',
        result: syntheticResult,
        passed: syntheticResult && !syntheticResult.contains('FAILED')
      ])
    }

    // Evaluate Gatling tests if timing provided  
    if (params.gatlingTestStartTime && params.gatlingTestEndTime) {
      echo "Evaluating Gatling load test performance..."
      
      def gatlingResult = dynatraceClient.evaluateSRG(
        params.srgServiceName,
        params.environment, 
        params.gatlingTestStartTime,
        params.gatlingTestEndTime
      )
      
      evaluationResults.add([
        testType: 'Gatling Load Tests',
        result: gatlingResult,
        passed: gatlingResult && !gatlingResult.contains('FAILED')
      ])
    }

    // Process results based on failure behavior
    def failedEvaluations = evaluationResults.findAll { !it.passed }
    
    if (failedEvaluations.isEmpty()) {
      echo "All SRG evaluations passed"
    } else {
      def failedTestTypes = failedEvaluations.collect { it.testType }.join(', ')
      
      switch (srgFailureBehavior) {
        case 'fail':
          echo "SRG evaluation failed for: ${failedTestTypes}"
          currentBuild.result = 'FAILURE'
          error("SRG evaluation failed - stopping pipeline")
          break
          
        case 'warn': 
          echo "Warning: SRG evaluation failed for: ${failedTestTypes}"
          currentBuild.result = 'UNSTABLE'
          break
          
        case 'ignore':
          echo "SRG evaluation failed for: ${failedTestTypes} (ignored)"
          break
          
        default:
          echo "Warning: SRG evaluation failed for: ${failedTestTypes}"
          currentBuild.result = 'UNSTABLE'
      }
    }

  } catch (Exception e) {
    echo "Error during SRG evaluation: ${e.message}"
    if (srgFailureBehavior == 'fail') {
      throw e
    } else {
      currentBuild.result = 'UNSTABLE' 
    }
  }
}