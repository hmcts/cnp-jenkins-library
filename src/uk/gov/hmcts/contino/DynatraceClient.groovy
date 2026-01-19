/*======================================================================================
DynatraceClient

Helper class for interacting with Dynatrace APIs during performance testing. This wraps
the Dynatrace APIs to make them easier to use from Jenkins pipeline scripts.

What it provides:
  - postEvent(): Sends custom info events to Dynatrace with build metadata
  - postMetric(): Sends release metrics for tracking deployments
  - triggerSyntheticTest(): Triggers a single execution of a synthetic test
  - getSyntheticStatus(): Checks execution stage and test result (SUCCESS/FAILED)
  - updateSyntheticTest(): Enables/disables synthetic tests and updates URLs
  - evaluateSRG(): Evaluates Site Reliability Guardian rules (not implemented yet)
  - setEnvironmentConfig(): Switches config based on environment (perftest/aat/preview)

All methods use environment variables for Dynatrace config (set by dynatracePerformanceSetup):
  - DT_SYNTHETIC_TEST_ID: Which monitor to use
  - DT_DASHBOARD_ID: Dashboard for results
  - DT_ENTITY_SELECTOR: Entity selector for events
  - DT_METRIC_TYPE, DT_METRIC_TAG: For release metrics

Authentication tokens come from Azure KeyVault:
  - PERF_SYNTHETIC_MONITOR_TOKEN: For triggering and checking tests
  - PERF_METRICS_TOKEN: For sending metrics
  - PERF_EVENT_TOKEN: For posting events
  - PERF_SYNTHETIC_UPDATE_TOKEN: For enabling/disabling tests

Usage:
  def client = new DynatraceClient(this)  // 'this' is the pipeline script context
  client.postEvent('et', 'sya-api')
  def result = client.triggerSyntheticTest()
============================================================================================*/
package uk.gov.hmcts.contino

import groovy.json.JsonSlurper
import groovy.json.JsonOutput


class DynatraceClient implements Serializable {
  
  // Default Dynatrace configuration
  public static final String DEFAULT_DYNATRACE_API_HOST = "https://yrk32651.live.dynatrace.com/"
  public static final String DEFAULT_EVENT_INGEST_ENDPOINT = "api/v2/events/ingest"
  public static final String DEFAULT_METRIC_INGEST_ENDPOINT = "api/v2/metrics/ingest"
  public static final String DEFAULT_TRIGGER_SYNTHETIC_ENDPOINT = "api/v2/synthetic/executions/batch"
  public static final String DEFAULT_UPDATE_SYNTHETIC_ENDPOINT = "api/v1/synthetic/monitors/"
  public static final String DEFAULT_GET_SYNTHETICS_EXECUTIONS_ENDPOINT = "api/v2/synthetic/executions/"

  def steps

  DynatraceClient(steps) {
    this.steps = steps
  }

  def postEvent(String product, String component) {
    def response = null
    try {
      response = steps.httpRequest(
        acceptType: 'APPLICATION_JSON',
        contentType: 'APPLICATION_JSON',
        httpMode: 'POST',
        quiet: true,
        customHeaders: [
          [name: 'Authorization', value: "Api-Token ${steps.env.PERF_EVENT_TOKEN}"]
        ],
        url: "${DEFAULT_DYNATRACE_API_HOST}${DEFAULT_EVENT_INGEST_ENDPOINT}",
        requestBody: """{ 
          "entitySelector": "${steps.env.DT_ENTITY_SELECTOR}",
          "eventType": "CUSTOM_INFO",
          "properties": {
            "Workspace": "${steps.env.WORKSPACE}",
            "Branch": "${steps.env.BRANCH_NAME}",
            "Build Number": "${steps.env.BUILD_NUMBER}",
            "Change URL": "${steps.env.CHANGE_URL}",
            "Commit ID": "${steps.env.GIT_COMMIT}",
            "Build URL": "${steps.env.BUILD_URL}", 
            "Synthetic Performance Test": "${steps.env.DT_SYNTHETIC_TEST_ID}",
            "Performance Dashboard": "${DEFAULT_DYNATRACE_API_HOST}#dashboard;id=${steps.env.DT_DASHBOARD_ID};applyDashboardDefaults=true"
          },
          "timeout": 1,
          "title": "${product.toUpperCase()}-${component.toUpperCase()} Performance Event"
        }"""
      )
      steps.echo "Dynatrace event posted successfully. Response ${response}"
    } catch (Exception e) {
      steps.echo "Failure posting Dynatrace Event: ${e.message}"
    }
    return response
  }

  def postMetric(String environment) {
    def response = null
    try {
      response = steps.httpRequest(
        acceptType: 'APPLICATION_JSON',
        contentType: 'TEXT_PLAIN',
        httpMode: 'POST',
        quiet: true,
        customHeaders: [
          [name: 'Authorization', value: "Api-Token ${steps.env.PERF_METRICS_TOKEN}"]
        ],
        url: "${DEFAULT_DYNATRACE_API_HOST}${DEFAULT_METRIC_INGEST_ENDPOINT}",
        requestBody: "env.release.value,type=${steps.env.DT_METRIC_TYPE},tag=${steps.env.DT_METRIC_TAG},env=${environment} 1.5"
      ) 
      steps.echo "Dynatrace metric posted successfully. Response ${response}"
    } catch (Exception e) {
      steps.echo "Failure posting Dynatrace Metric: ${e.message}"
    }
    return response
  }

  def triggerSyntheticTest() {
    def response = null
    try {
      response = steps.httpRequest(
        acceptType: 'APPLICATION_JSON',
        contentType: 'APPLICATION_JSON',
        httpMode: 'POST',
        quiet: true,
        customHeaders: [
          [name: 'Authorization', value: "Api-Token ${steps.env.PERF_SYNTHETIC_MONITOR_TOKEN}"]
        ],
        url: "${DEFAULT_DYNATRACE_API_HOST}${DEFAULT_TRIGGER_SYNTHETIC_ENDPOINT}",
        requestBody: """{
          "monitors": [
            {
              "monitorId": "${steps.env.DT_SYNTHETIC_TEST_ID}"
            }
          ]
        }"""
      )
      steps.echo "Dynatrace synthetic test triggered. Response ${response}"
    } catch (Exception e) {
      steps.echo "Error while triggering synthetic test: ${e.message}"
      return null
    }
    
    steps.echo "Raw JSON response:\n${response.content}"

    def json = new JsonSlurper().parseText(response.content)
    def triggeredCount = json.triggeredCount
    def executedSynthetics = json.triggered[0].executions
    
    def selectedIndex = triggeredCount - 1
    def lastExecutionId = executedSynthetics[selectedIndex].executionId

    steps.echo "Triggered Count: ${triggeredCount}"
    steps.echo "Last Execution ID: ${lastExecutionId}"

    return [
      response: response,
      triggeredCount: triggeredCount,
      lastExecutionId: lastExecutionId
    ]
  }

  def getSyntheticStatus(String lastExecutionId) {
    def response = null
    try {
      response = steps.httpRequest(
        acceptType: 'APPLICATION_JSON',
        contentType: 'APPLICATION_JSON',
        httpMode: 'GET',
        quiet: true,
        customHeaders: [
          [name: 'Authorization', value: "Api-Token ${steps.env.PERF_SYNTHETIC_MONITOR_TOKEN}"]
        ],
        url: "${DEFAULT_DYNATRACE_API_HOST}${DEFAULT_GET_SYNTHETICS_EXECUTIONS_ENDPOINT}${lastExecutionId}"
      )
      steps.echo "Check Synthetic Status: Response ${response}"
    } catch (Exception e) {
      steps.echo "Error while checking synthetic status: ${e.message}"
      if (response) {
        steps.echo "Raw JSON response:\n${response.content}"
      }
      return null
    }

    def json = new JsonSlurper().parseText(response.content)
    def executionStage = json.executionStage
    def testStatus = json.simpleResults?.status

    steps.echo "Execution Stage: ${executionStage}"
    if (testStatus) {
      steps.echo "Test Result Status: ${testStatus}"

      // Log full JSON response if test failed for debugging
      if (testStatus == "FAILED") {
        steps.echo "Test failed - Raw JSON response:\n${response.content}"
      }
    }

    return [
      response: response,
      executionStatus: executionStage,
      testStatus: testStatus
    ]
  }

  def updateSyntheticTest(boolean enabled) {
    def response = null
    try {
      def getResponse = steps.httpRequest(
        acceptType: 'APPLICATION_JSON',
        contentType: 'APPLICATION_JSON',
        httpMode: 'GET',
        quiet: true,
        customHeaders: [
          [name: 'Authorization', value: "Api-Token ${steps.env.PERF_SYNTHETIC_MONITOR_TOKEN}"]
        ],
        url: "${DEFAULT_DYNATRACE_API_HOST}${DEFAULT_UPDATE_SYNTHETIC_ENDPOINT}${steps.env.DT_SYNTHETIC_TEST_ID}"
      )

      // Parse JSON to modify, then serialize with proper escaping
      def json = new JsonSlurper().parseText(getResponse.content)

      // Update enabled status
      json.enabled = enabled

      // Update URLs for preview environment if needed
      if (steps.env.TEST_URL && steps.env.DT_SYNTHETIC_TEST_ID.startsWith("HTTP") && enabled) {
        // When enabling HTTP synthetics, replace PREVIEW with hostname
        steps.echo "Enabling HTTP synthetic - replacing PREVIEW with TEST_URL: ${steps.env.TEST_URL}"
        def hostname = steps.env.TEST_URL.replaceAll('^https?://', '').split('/')[0]
        steps.echo "Extracted hostname: ${hostname}"

        // Replace PREVIEW in all request URLs
        for (int i = 0; i < json.script.requests.size(); i++) {
          if (json.script.requests[i].url?.contains("PREVIEW")) {
            def oldUrl = json.script.requests[i].url
            json.script.requests[i].url = json.script.requests[i].url.replace("PREVIEW", hostname)
            steps.echo "Request ${i + 1}: ${oldUrl} -> ${json.script.requests[i].url}"
          }
        }
      } else if (steps.env.TEST_URL && !steps.env.DT_SYNTHETIC_TEST_ID.startsWith("HTTP")) {
        // For BROWSER synthetics: update events array
        if (json.script?.events) {
          json.script.events[0].url = steps.env.TEST_URL
        }
      }

      // Use JsonOutput with custom escaping to match Dynatrace format
      def modifiedRequestBody = JsonOutput.toJson(json)

      // Debug: Check if JSON serialization changed anything critical
      steps.echo "Original content length: ${getResponse.content.length()}"
      steps.echo "Modified content length: ${modifiedRequestBody.length()}"

      // Write both versions for comparison and archive as build artifacts
      steps.writeFile(file: 'dt-original.json', text: getResponse.content)
      steps.writeFile(file: 'dt-modified.json', text: modifiedRequestBody)
      steps.archiveArtifacts(artifacts: 'dt-*.json', allowEmptyArchive: true)
      steps.echo "Archived dt-original.json and dt-modified.json as build artifacts"

      response = steps.httpRequest(
        acceptType: 'APPLICATION_JSON',
        contentType: 'APPLICATION_JSON',
        httpMode: 'PUT',
        quiet: true,
        customHeaders: [
          [name: 'Authorization', value: "Api-Token ${steps.env.PERF_SYNTHETIC_UPDATE_TOKEN}"]
        ],
        url: "${DEFAULT_DYNATRACE_API_HOST}${DEFAULT_UPDATE_SYNTHETIC_ENDPOINT}${steps.env.DT_SYNTHETIC_TEST_ID}",
        requestBody: modifiedRequestBody
      )
      steps.echo "Dynatrace synthetic test updated. Response ${response}"
    } catch (Exception e) {
      steps.echo "Error while updating synthetic test: ${e.message}"
      if (response) {
        steps.echo "Response detail: ${response.content}"
      }
    }
    return response
  }

  static def setEnvironmentConfig(def config, String envName) {
    switch (envName) {
      case "preview":
        config.dynatraceSyntheticTest = config.dynatraceSyntheticTestPreview
        config.dynatraceDashboardId = config.dynatraceDashboardIdPreview
        config.dynatraceDashboardURL = config.dynatraceDashboardURLPreview
        config.dynatraceEntitySelector = config.dynatraceEntitySelectorPreview
        break
      case "aat":
        config.dynatraceSyntheticTest = config.dynatraceSyntheticTestAAT
        config.dynatraceDashboardId = config.dynatraceDashboardIdAAT
        config.dynatraceDashboardURL = config.dynatraceDashboardURLAAT
        config.dynatraceEntitySelector = config.dynatraceEntitySelectorAAT
        break
      case "perftest":
        config.dynatraceSyntheticTest = config.dynatraceSyntheticTest
        config.dynatraceDashboardId = config.dynatraceDashboardIdPerfTest
        config.dynatraceDashboardURL = config.dynatraceDashboardURLPerfTest
        config.dynatraceEntitySelector = config.dynatraceEntitySelectorPerfTest
        break
      default:
        throw new IllegalArgumentException("Unknown environment: ${envName}")
    }
    
    return config
  }

  //Method to evaluate dynatrace site reliability guardian
  def evaluateSRG(String service, String stage, String startTime, String endTime) {
    def response = null
    try {
      def dockerImage = "dynatraceace/dynatrace-automation-cli:1.2.3" // Use specific version

      // response = steps.sh(script: """
      //   docker run --rm \
      //     -e DYNATRACE_URL_GEN3=${DEFAULT_DYNATRACE_API_HOST} \
      //     -e ACCOUNT_URN=\${ACCOUNT_URN} \
      //     -e DYNATRACE_CLIENT_ID=\${DYNATRACE_CLIENT_ID} \
      //     -e DYNATRACE_SECRET=\${DYNATRACE_SECRET} \
      //     -e DYNATRACE_SSO_URL=https://sso.dynatrace.com/sso/oauth2/token \
      //     ${dockerImage} \
      //     dta srg evaluate \
      //     --service "${service}" \
      //     --stage "${stage}" \
      //     --start-time "${startTime}" \
      //     --end-time "${endTime}"
      // """, returnStdout: true).trim()

      steps.echo "SRG evaluation completed. Response: ${response}"
    } catch (Exception e) {
      steps.echo "Error during SRG evaluation: ${e.message}"
      return null
    }
    return response
  }

}