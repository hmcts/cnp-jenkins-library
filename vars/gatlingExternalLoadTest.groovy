/*======================================================================================
gatlingExternalLoadTest

Executes Gatling load tests from an external Git repository using the existing 
GradleBuilder infrastructure. This ensures 100% compatibility with existing 
performance testing patterns.

@param params Map containing:
  - product: String product name (required)
  - component: String component name (required) 
  - environment: String environment name (required)
  - subscription: String Azure subscription (required)
  - gatlingRepo: String Git repository URL for Gatling tests (required)
  - gatlingBranch: String branch name (optional, defaults to 'main')
  - gatlingSimulation: String specific simulation class (optional)
  - gatlingUsers: Integer number of users (optional, defaults to 10)
  - gatlingRampDuration: String ramp duration (optional, defaults to '30s')
  - gatlingTestDuration: String test duration (optional, defaults to '60s')
  - testUrl: String target URL (optional, uses env.TEST_URL)

Prerequisites:
  - External Gatling repository must have Gradle with gatling-gradle-plugin or gradle-gatling-plugin
  - Repository structure should support the properties: TEST_URL, GATLING_USERS, etc.
  - Uses existing GradleBuilder.performanceTest() method for 100% compatibility

Example usage:
gatlingExternalLoadTest([
  product: 'et',
  component: 'sya-api', 
  environment: 'perftest',
  subscription: 'DCD-CFT-Sandbox',
  gatlingRepo: 'https://github.com/hmcts/et-performance-tests.git',
  gatlingBranch: 'main',
  gatlingSimulation: 'uk.gov.hmcts.et.simulation.ApiLoadTest',
  gatlingUsers: 50,
  testUrl: env.TEST_URL
])

Note: This approach leverages the existing GradleBuilder.performanceTest() method
to ensure exact compatibility with the current pipeline infrastructure.
============================================================================================*/

import uk.gov.hmcts.contino.GradleBuilder

def call(Map params) {
  
  // Validate required parameters
  if (!params.product) {
    error("gatlingExternalLoadTest: 'product' parameter is required")
  }
  if (!params.component) {
    error("gatlingExternalLoadTest: 'component' parameter is required") 
  }
  if (!params.environment) {
    error("gatlingExternalLoadTest: 'environment' parameter is required")
  }
  if (!params.subscription) {
    error("gatlingExternalLoadTest: 'subscription' parameter is required")
  }
  if (!params.gatlingRepo) {
    error("gatlingExternalLoadTest: 'gatlingRepo' parameter is required")
  }

  // Set parameter defaults
  def gatlingBranch = params.gatlingBranch ?: 'main'
  def testUrl = params.testUrl ?: env.TEST_URL
  def gatlingUsers = params.gatlingUsers ?: 10
  def gatlingRampDuration = params.gatlingRampDuration ?: '30s'
  def gatlingTestDuration = params.gatlingTestDuration ?: '60s'
  def gatlingSimulation = params.gatlingSimulation

  echo "Starting external Gatling load test execution using GradleBuilder..."
  echo "Product: ${params.product}"
  echo "Component: ${params.component}"
  echo "Environment: ${params.environment}"
  echo "External Repository: ${params.gatlingRepo}"
  echo "Branch: ${gatlingBranch}"
  echo "Target URL: ${testUrl}"
  echo "Users: ${gatlingUsers}"
  echo "Simulation: ${gatlingSimulation ?: 'All simulations'}"
  echo "Date/Time: ${new Date().format('yyyy-MM-dd HH:mm:ss')}"

  try {
    // Create separate workspace for external Gatling tests
    def gatlingWorkspace = "${env.WORKSPACE}/external-gatling-tests"
    
    // Clean any existing workspace
    sh "rm -rf ${gatlingWorkspace}"
    sh "mkdir -p ${gatlingWorkspace}"
    
    dir(gatlingWorkspace) {
      echo "Checking out external Gatling repository..."
      
      // Checkout external repository (no credentials needed for public repos)
      checkout([
        $class: 'GitSCM',
        branches: [[name: "*/${gatlingBranch}"]],
        doGenerateSubmoduleConfigurations: false,
        extensions: [
          [$class: 'CleanCheckout'],
          [$class: 'CloneOption', depth: 1, noTags: false, reference: '', shallow: true]
        ],
        submoduleCfg: [],
        userRemoteConfigs: [[
          url: params.gatlingRepo
          // No credentialsId needed for public repositories
        ]]
      ])
      
      echo "External Gatling repository checked out successfully"
      
      // Verify it's a Gradle project
      if (!fileExists('build.gradle') && !fileExists('build.gradle.kts')) {
        error("External Gatling repository must be a Gradle project with build.gradle or build.gradle.kts")
      }
      
      // Set environment variables for Gatling test configuration
      withEnv([
        "TEST_URL=${testUrl}",
        "GATLING_USERS=${gatlingUsers}",
        "GATLING_RAMP_DURATION=${gatlingRampDuration}",
        "GATLING_TEST_DURATION=${gatlingTestDuration}",
        "PRODUCT=${params.product}",
        "COMPONENT=${params.component}",
        "ENVIRONMENT=${params.environment}",
        "GATLING_SIMULATION_CLASS=${gatlingSimulation ?: ''}"
      ]) {
        
        echo "Creating GradleBuilder instance for external Gatling repository..."
        
        // Create GradleBuilder instance using the exact same pattern as the main pipeline
        def gradleBuilder = new GradleBuilder(this, params.product)
        
        echo "Executing Gatling performance tests using GradleBuilder.performanceTest()..."
        
        // Use the EXACT same method as the existing pipeline - this ensures 100% compatibility
        // The GradleBuilder handles all the Gatling plugin detection, report paths, and gatlingArchive() calls
        gradleBuilder.performanceTest()
        
        echo "External Gatling load tests completed successfully using GradleBuilder"
      }
    }
    
    // Publish performance reports using existing function
    echo "Publishing external Gatling performance reports..."
    publishPerformanceReports(
      product: params.product,
      component: params.component,
      environment: params.environment,
      subscription: params.subscription
    )
    
    echo "External Gatling performance reports published successfully"
    
  } catch (Exception e) {
    echo "Error in external Gatling load test execution: ${e.message}"
    e.printStackTrace()
    currentBuild.result = 'UNSTABLE'
    throw e
  }
}