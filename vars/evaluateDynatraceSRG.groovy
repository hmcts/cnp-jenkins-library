/*======================================================================================
evaluateDynatraceSRG

Evaluates Site Reliability Guardian for completed performance tests with configurable
pipeline control options.

@param params Map containing:
  - environment: String environment name (required)
  - srgServiceName: String service name configured in Dynatrace SRG (required)
  - performanceTestStartTime: String ISO timestamp when perf tests started (optional)
  - performanceTestEndTime: String ISO timestamp when perf tests ended (optional)
  - gatlingTestStartTime: String ISO timestamp when gatling tests started (optional)
  - gatlingTestEndTime: String ISO timestamp when gatling tests ended (optional)
  - srgFailureBehavior: String 'fail'|'warn'|'ignore' (optional, defaults to 'warn')
  - product: String product name (optional, for logging only)
  - component: String component name (optional, for logging only)

Prerequisites:
  - OAuth credentials in vault: ACCOUNT_URN, DYNATRACE_CLIENT_ID, DYNATRACE_SECRET
  - Performance tests must have completed and provided timing data
  - SRG service name must match exactly what is configured in Dynatrace

Example usage:
evaluateDynatraceSRG([
  environment: 'preview',
  srgServiceName: 'et-sya-service',
  performanceTestStartTime: '2025-01-15T10:30:00Z',
  performanceTestEndTime: '2025-01-15T10:35:00Z',
  gatlingTestStartTime: '2025-01-15T10:35:30Z', 
  gatlingTestEndTime: '2025-01-15T10:40:00Z',
  srgFailureBehavior: 'warn',
  product: 'et',
  component: 'sya-api'
])
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