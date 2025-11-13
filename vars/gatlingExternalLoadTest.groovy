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
  gatlingSimulation: 'uk.gov.hmcts.et.simulation.ApiLoadTest'
])

Note: This approach leverages the existing GradleBuilder.performanceTest() method
to ensure exact compatibility with the current pipeline infrastructure.
============================================================================================*/

import uk.gov.hmcts.contino.GradleBuilder
import uk.gov.hmcts.contino.Builder


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

  // Set parameter defaults - try master first as many repos still use it
  def gatlingBranch = params.gatlingBranch ?: 'master'
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
  echo "Target URL: ${env.TEST_URL}"
  echo "CCD_DATA_STORE_API_PR_URL: ${env.CCD_DATA_STORE_API_URL}"
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
      
      // Checkout external repository using Git credentials
      echo "Cloning ${params.gatlingRepo} branch ${gatlingBranch}..."
      
      withCredentials([usernamePassword(credentialsId: env.GIT_CREDENTIALS_ID, passwordVariable: 'BEARER_TOKEN', usernameVariable: 'USER_NAME')]) {
        try {
          // Try the specified branch first
          sh """
            REPO_URL=\$(echo ${params.gatlingRepo} | sed "s/github.com/\${USER_NAME}:\${BEARER_TOKEN}@github.com/g")
            git clone --depth=1 --branch=${gatlingBranch} \$REPO_URL .
          """
        } catch (Exception e) {
          echo "Failed to clone branch ${gatlingBranch}, trying default branch..."
          // If specific branch fails, try without specifying branch (gets default)
          sh """
            REPO_URL=\$(echo ${params.gatlingRepo} | sed "s/github.com/\${USER_NAME}:\${BEARER_TOKEN}@github.com/g")
            git clone --depth=1 \$REPO_URL .
          """
        }
      }
      
      echo "External Gatling repository checked out successfully"

      //Run the Perf test using existing perftest builder
      try {
        // Capture test start time for SRG evaluation
        def testStartTime = new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone('UTC'))
        env.GATLING_TEST_START_TIME = testStartTime
        echo "Gatling load test start time: ${testStartTime}"

        def builder = new GradleBuilder(this, params.product)
        builder.performanceTest(params.gatlingSimulation)

        //Capture test end time for SRG evaluation
        def testEndTime = new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone('UTC'))
        env.GATLING_TEST_END_TIME = testEndTime
        echo "Gatling load test end time: ${testEndTime}"
        
      } catch (Exception e) {
        echo "**** Failed to run builder.performanceTest: ${e.message}"
      }
    } //End of dir  
    
  // Publish performance reports using existing function
  echo "Publishing external Gatling performance reports..."

  // Override GATLING_REPORTS_PATH to point to external workspace reports
  def externalReportsPath = "external-gatling-tests/build/reports/gatling"
  echo "Debug: Setting GATLING_REPORTS_PATH to: ${externalReportsPath}"

 // Publish reports code 
  withEnv(["GATLING_REPORTS_PATH=${externalReportsPath}"]) {

    echo "Uploading external Gatling reports to perfInBuildPipeline directory..."

    try {
      // Upload to custom directory for external tests
      azureBlobUpload(
        params.subscription,
        'buildlog-storage-account',
        env.GATLING_REPORTS_PATH,
        "performance/perfInBuildPipeline/${params.product}-${params.component}/${params.environment}"
      )
      echo "Successfully uploaded external Gatling reports to: perfInBuildPipeline/${params.product}-${params.component}/${params.environment}"
    }
    catch (Exception ex) {
      echo "ERROR: Failed to upload external Gatling reports: ${ex}"
    }
  }
    
  } catch (Exception e) {
    echo "Error in external Gatling load test execution: ${e.message}"
    e.printStackTrace()
    currentBuild.result = 'UNSTABLE'
    throw e
  }
}