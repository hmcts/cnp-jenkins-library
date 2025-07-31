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

  def postEvent(String syntheticTest, String dashboardId, String entitySelector, String product, String component) {
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
          "entitySelector": "${entitySelector}",
          "eventType": "CUSTOM_INFO",
          "properties": {
            "Workspace": "${steps.env.WORKSPACE}",
            "Branch": "${steps.env.BRANCH_NAME}",
            "Build Number": "${steps.env.BUILD_NUMBER}",
            "Change URL": "${steps.env.CHANGE_URL}",
            "Commit ID": "${steps.env.GIT_COMMIT}",
            "Build URL": "${steps.env.BUILD_URL}", 
            "Synthetic Performance Test": "${syntheticTest}",
            "Performance Dashboard": "${DEFAULT_DYNATRACE_API_HOST}#dashboard;id=${dashboardId};applyDashboardDefaults=true"
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

  def postMetric(String metricType, String metricTag, String environment) {
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
        requestBody: "env.release.value,type=${metricType},tag=${metricTag},env=${environment} 3"
      ) 
      steps.echo "Dynatrace metric posted successfully. Response ${response}"
    } catch (Exception e) {
      steps.echo "Failure posting Dynatrace Metric: ${e.message}"
    }
    return response
  }

  def triggerSyntheticTest(String syntheticTest) {
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
              "monitorId": "${syntheticTest}"
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
      return null
    }
    
    steps.echo "Raw JSON response:\n${response.content}"

    def json = new JsonSlurper().parseText(response.content)
    def executionStatus = json.executionStage
        
    steps.echo "Current Status: ${executionStatus}"
    return [
      response: response,
      executionStatus: executionStatus
    ]
  }

  def updateSyntheticTest(String syntheticTest, boolean enabled, String customUrl = null) {
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
        url: "${DEFAULT_DYNATRACE_API_HOST}${DEFAULT_UPDATE_SYNTHETIC_ENDPOINT}${syntheticTest}"
      )
      
      def json = new JsonSlurper().parseText(getResponse.content)
      
      json.enabled = enabled
      if (customUrl) {
        json.script.events[0].url = customUrl
      }

      def modifiedRequestBody = JsonOutput.toJson(json)

      response = steps.httpRequest(
        acceptType: 'APPLICATION_JSON',
        contentType: 'APPLICATION_JSON',
        httpMode: 'PUT',
        quiet: true,
        customHeaders: [
          [name: 'Authorization', value: "Api-Token ${steps.env.PERF_SYNTHETIC_UPDATE_TOKEN}"]
        ],
        url: "${DEFAULT_DYNATRACE_API_HOST}${DEFAULT_UPDATE_SYNTHETIC_ENDPOINT}${syntheticTest}",
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
}