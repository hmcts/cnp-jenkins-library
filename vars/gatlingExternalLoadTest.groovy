/*======================================================================================
gatlingExternalLoadTest

Runs Gatling load tests from a separate Git repository. This lets teams keep their
performance tests in a dedicated repo rather than alongside service code.

What it does:
  - Clones your external Gatling test repo
  - Runs the tests using GradleBuilder.performanceTest() (same as in-repo tests)
  - Records start/end times for SRG evaluation
  - Uploads reports to Azure blob storage

Important: Test parameters like number of users, ramp duration, and test duration are
configured in the external repo's, not passed in here. This function
only tells the pipeline which repo to use and which simulation to run.

@param params Map containing:
  - product: String product name (required)
  - component: String component name (required)
  - environment: String environment (required)
  - subscription: String Azure subscription (required, for blob storage)
  - gatlingRepo: String Git repo URL (required)
  - gatlingBranch: String branch name (optional, defaults to 'master')
  - gatlingSimulation: String simulation class to run (optional, runs all if not specified)

Uses these environment variables:
  - TEST_URL: Where to point the tests (set by testEnv wrapper)
  - GIT_CREDENTIALS_ID: For cloning private repos
  - WORKSPACE: Jenkins workspace path

Sets these environment variables:
  - GATLING_TEST_START_TIME: When the test started (Timestamp)
  - GATLING_TEST_END_TIME: When the test finished (Timestamp)
  - GATLING_REPORTS_PATH: Where the reports are 

Prerequisites:
  - Your external repo must have Gradle with gatling-gradle-plugin or gradle-gatling-plugin
  - Your tests should read TEST_URL from the environment (URL construction and valid code exists within the performance-testing repository)
  - GIT_CREDENTIALS_ID must be set up in Jenkins
  - Blob storage account 'buildlog-storage-account' must exist

Example:
gatlingExternalLoadTest([
  product: 'et',
  component: 'sya-api',
  environment: 'perftest',
  subscription: 'DCD-CFT-Sandbox',
  gatlingRepo: 'https://github.com/hmcts/et-performance-tests.git',
  gatlingBranch: 'test-synthetics-branch',
  gatlingSimulation: 'uk.gov.hmcts.et.simulation.ApiLoadTest'
])

Related files:
  - Called by: sectionDeployToAKS.groovy and withPipeline.groovy
  - Runs alongside: dynatraceSyntheticTest.groovy (if both enabled)
  - Reports via: azureBlobUpload
  - May be followed by: evaluateDynatraceSRG.groovy
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
  def gatlingSimulation = params.gatlingSimulation

  echo "Starting external Gatling load test execution using GradleBuilder..."
  echo "Product: ${params.product}"
  echo "Component: ${params.component}"
  echo "Environment: ${params.environment}"
  echo "External Repository: ${params.gatlingRepo}"
  echo "Branch: ${gatlingBranch}"
  echo "Target URL: ${env.TEST_URL}"
  echo "CCD_DATA_STORE_API_PR_URL: ${env.CCD_DATA_STORE_API_URL}"
  echo "Simulation: ${gatlingSimulation ?: 'All simulations'}"
  echo "Date/Time: ${new Date().format('yyyy-MM-dd HH:mm:ss')}"

  // Create separate workspace for external Gatling tests
  def gatlingWorkspace = "${env.WORKSPACE}/external-gatling-tests"

  //catchError 1: Clean and create new workspace
  catchError(stageResult:'FAILURE', buildResult:'SUCCESS', message:'Error cleaning and creating fresh workspace dir...') {

  // Clean any existing workspace
  sh "rm -rf ${gatlingWorkspace}"
  sh "mkdir -p ${gatlingWorkspace}"
  }
  dir(gatlingWorkspace) {
    echo "Checking out external Gatling repository..."
    
    // Checkout external repository using Git credentials
    echo "Cloning ${params.gatlingRepo} branch ${gatlingBranch}..."
    
    withCredentials([usernamePassword(credentialsId: env.GIT_CREDENTIALS_ID, passwordVariable: 'BEARER_TOKEN', usernameVariable: 'USER_NAME')]) {
      //catchError 2: Clone repo
      catchError(stageResult:'FAILURE', buildResult:'SUCCESS', message:'Error cloning Gatling repo - both specified branch and default branch failed') {
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
    }
    
    echo "External Gatling repository checked out successfully"

    //Run the Perf test using existing perftest builder
    try {
      // Capture test start time for SRG evaluation
      def testStartTime = new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone('UTC'))
      env.GATLING_TEST_START_TIME = testStartTime
      echo "Gatling load test start time: ${testStartTime}"

      def builder = new GradleBuilder(this, params.product)
      catchError(stageResult:'FAILURE', buildResult:'SUCCESS', message:'Error in Gatling Performance test') {
        builder.performanceTest(params.gatlingSimulation)
      }

      //Capture test end time for SRG evaluation
      def testEndTime = new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone('UTC'))
      env.GATLING_TEST_END_TIME = testEndTime
      echo "Gatling load test end time: ${testEndTime}"
      
    } catch (Exception e) {
      echo "****Gatling test Failure or fail to run builder.performanceTest: ${e.message}"
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

    catchError(stageResult:'UNSTABLE', buildResult:'SUCCESS', message:'WARNING: Failed to upload external Gatling reports') {
      // Upload to custom directory for external tests
      azureBlobUpload(
        params.subscriptionXXXXXXXX,
        'buildlog-storage-account',
        env.GATLING_REPORTS_PATH,
        "performance/_build/${params.product}-${params.component}/${params.environment}"
      )
      echo "Successfully uploaded external Gatling reports to: perfInBuildPipeline/${params.product}-${params.component}/${params.environment}"
    }
  }
}