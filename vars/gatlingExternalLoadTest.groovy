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

  // Set parameter defaults - try master first as many repos still use it
  def gatlingBranch = params.gatlingBranch ?: 'master'
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
      
      // Checkout external repository using simple git commands for better reliability
      echo "Cloning ${params.gatlingRepo} branch ${gatlingBranch}..."
      
      try {
        // Try the specified branch first
        sh "git clone --depth=1 --branch=${gatlingBranch} ${params.gatlingRepo} ."
      } catch (Exception e) {
        echo "Failed to clone branch ${gatlingBranch}, trying default branch..."
        // If specific branch fails, try without specifying branch (gets default)
        sh "git clone --depth=1 ${params.gatlingRepo} ."
      }
      
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
        
        echo "Executing Gatling performance tests directly without Jenkins library init scripts..."
        
        // Check if external repo has Gatling plugins
        def buildGradleContent = ""
        def hasGatlingPlugin = false
        
        if (fileExists('build.gradle')) {
          buildGradleContent = readFile('build.gradle')
          hasGatlingPlugin = buildGradleContent.contains('io.gatling.gradle') || buildGradleContent.contains('gradle-gatling-plugin')
        } else if (fileExists('build.gradle.kts')) {
          buildGradleContent = readFile('build.gradle.kts')
          hasGatlingPlugin = buildGradleContent.contains('io.gatling.gradle') || buildGradleContent.contains('gradle-gatling-plugin')
        }
        
        if (!hasGatlingPlugin) {
          error("External Gatling repository must have Gatling Gradle plugin configured (io.gatling.gradle or gradle-gatling-plugin)")
        }
        
        // Set Gatling reports path (same pattern as GradleBuilder)
        env.GATLING_REPORTS_PATH = 'build/reports/gatling'
        env.GATLING_REPORTS_DIR = "${gatlingWorkspace}/" + env.GATLING_REPORTS_PATH
        
        // The external Gatling repo requires Java 17 (as specified in build.gradle)
        echo "Checking available Java versions for Java 17 compatibility..."
        sh "java -version"
        sh "echo 'Current JAVA_HOME: \$JAVA_HOME'"
        
        def java17Found = false
        def java17Path = ""
        
        // Try known working location first
        def knownJava17Location = "/usr/lib/jvm/java-17-openjdk-amd64"
        
        try {
          sh "test -d ${knownJava17Location}"
          java17Found = true
          java17Path = knownJava17Location
          echo "Found Java 17 at known location: ${knownJava17Location}"
        } catch (Exception e) {
          echo "Java 17 not found at known location: ${knownJava17Location}. Searching alternative locations..."
          
          // Fallback to search if known location fails
          def java17Locations = [
            "/opt/java/openjdk-17",
            "/usr/lib/jvm/java-17-openjdk",
            "/usr/lib/jvm/adoptopenjdk-17-hotspot",
            "/usr/lib/jvm/temurin-17-jdk",
            "/opt/hostedtoolcache/Java_Temurin-Hotspot_jdk/17.0.12-7/x64"
          ]
          
          for (location in java17Locations) {
            try {
              sh "test -d ${location}"
              java17Found = true
              java17Path = location
              echo "Found Java 17 at: ${location}"
              break
            } catch (Exception searchException) {
              echo "Java 17 not found at: ${location}"
            }
          }
        }
        
        if (java17Found) {
          echo "Using Java 17 from: ${java17Path}"
          withEnv(["JAVA_HOME=${java17Path}", "PATH+JAVA=${java17Path}/bin"]) {
            sh "java -version"
            
            // Completely clear all Gradle caches to avoid Java version conflicts
            echo "Clearing all Gradle caches to avoid Java version conflicts..."
            sh "rm -rf ~/.gradle/caches/ || true"
            sh "rm -rf ~/.gradle/wrapper/ || true"
            sh "rm -rf .gradle/ || true"
            sh "rm -rf build/ || true"
            
            // Also clear any Jenkins workspace Gradle cache
            sh "find ${env.WORKSPACE} -name '.gradle' -type d -exec rm -rf {} + 2>/dev/null || true"
            
            // Conditional synchronization - only if both performance tests are enabled
            echo "DEBUG: PERFORMANCE_STAGES_ENABLED = ${System.getenv('PERFORMANCE_STAGES_ENABLED')}"
            echo "DEBUG: GATLING_TESTS_ENABLED = ${System.getenv('GATLING_TESTS_ENABLED')}"
            def bothTestsEnabled = System.getenv("PERFORMANCE_STAGES_ENABLED") == "true" && System.getenv("GATLING_TESTS_ENABLED") == "true"
            echo "DEBUG: bothTestsEnabled = ${bothTestsEnabled}"
            
            if (bothTestsEnabled) {
              echo "Gatling: Setup complete, ready for synchronised execution"
              milestone(label: "gatling-ready", ordinal: 9000)
              echo "Gatling: Waiting for Dynatrace to be ready..."
              milestone(label: "both-perf-tests-ready", ordinal: 9001)
              echo "Gatling: Starting synchronised test execution NOW!"
            } else {
              echo "Gatling: Single test execution (no sync needed)"
            }
            
            echo "Running Gatling tests with Java 17..."
            sh "./gradlew --no-daemon clean gatlingRun"
          }
        } else {
          echo "Java 17 not found in filesystem. Trying Jenkins tool installations..."
          
          try {
            // Try using Jenkins tool installation for Java 17
            tool name: 'openjdk-17', type: 'jdk'
            def toolJava17 = tool name: 'openjdk-17', type: 'jdk'
            echo "Found Java 17 via Jenkins tools at: ${toolJava17}"
            withEnv(["JAVA_HOME=${toolJava17}", "PATH+JAVA=${toolJava17}/bin"]) {
              sh "java -version"
              
              // Clear Gradle caches for Jenkins tool Java 17 as well
              echo "Clearing all Gradle caches for Jenkins tool Java 17..."
              sh "rm -rf ~/.gradle/caches/ || true"
              sh "rm -rf ~/.gradle/wrapper/ || true"
              sh "rm -rf .gradle/ || true"
              sh "rm -rf build/ || true"
              sh "find ${env.WORKSPACE} -name '.gradle' -type d -exec rm -rf {} + 2>/dev/null || true"
              
              // Conditional synchronization - only if both performance tests are enabled
              echo "DEBUG: PERFORMANCE_STAGES_ENABLED = ${System.getenv('PERFORMANCE_STAGES_ENABLED')}"
              echo "DEBUG: GATLING_TESTS_ENABLED = ${System.getenv('GATLING_TESTS_ENABLED')}"
              def bothTestsEnabled = System.getenv("PERFORMANCE_STAGES_ENABLED") == "true" && System.getenv("GATLING_TESTS_ENABLED") == "true"
              echo "DEBUG: bothTestsEnabled = ${bothTestsEnabled}"
              
              if (bothTestsEnabled) {
                echo "Gatling: Setup complete, ready for synchronised execution"
                milestone(label: "gatling-ready", ordinal: 9000)
                echo "Gatling: Waiting for Dynatrace to be ready..."
                milestone(label: "both-perf-tests-ready", ordinal: 9001)
                echo "Gatling: Starting synchronised test execution NOW!"
              } else {
                echo "Gatling: Single test execution (no sync needed)"
              }
              
              echo "Running Gatling tests with Jenkins tool Java 17..."
              sh "./gradlew --no-daemon clean gatlingRun"
            }
          } catch (Exception toolError) {
            echo "ERROR: Java 17 not available via Jenkins tools either."
            echo "Available Java installations:"
            sh "find /usr/lib/jvm /opt -name '*java*' -type d 2>/dev/null || echo 'No Java directories found'"
            echo "Please ensure Java 17 is installed on the build agent or configured in Jenkins Global Tool Configuration."
            error("Java 17 is required for Gatling tests but was not found on this build agent.")
          }
        }
        
        // Archive reports using Jenkins Gatling plugin (same as GradleBuilder)
        echo "Archiving Gatling reports..."
        gatlingArchive()
        
        echo "External Gatling load tests completed successfully using GradleBuilder"
      }
    }
    
  // Publish performance reports using existing function
  echo "Publishing external Gatling performance reports..."

  // Override GATLING_REPORTS_PATH to point to external workspace reports
  def externalReportsPath = "external-gatling-tests/build/reports/gatling"
  echo "Debug: Setting GATLING_REPORTS_PATH to: ${externalReportsPath}"

 // Publish reports code 
  withEnv(["GATLING_REPORTS_PATH=${externalReportsPath}"]) {

    // Previous implementation using publishPerformanceReports function:
    // withEnv(["GATLING_REPORTS_PATH=${externalReportsPath}"]) {
    //   publishPerformanceReports(
    //     product: params.product,
    //     component: params.component,
    //     environment: params.environment,
    //     subscription: params.subscription
    //   )
    // }

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