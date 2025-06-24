import uk.gov.hmcts.contino.Gatling
import groovy.json.JsonSlurper

def call(params)  {
  try {
      azureBlobUpload(params.subscription, 'buildlog-storage-account', env.GATLING_REPORTS_PATH, "performance/${params.product}-${params.component}/${params.environment}")
  }
  catch (Exception ex) {
    echo "ERROR: Failed to upload performance reports to blob storage destination performance/${params.product}-${params.component}/${params.environment} " +
      "due to the following error: ${ex}"
  }
  def reportsPath = env.GATLING_REPORTS_PATH

  try {
    publishToCosmosDb(params, Gatling.COSMOSDB_CONTAINER, reportsPath, '**/*.json')
  }
  catch (Exception ex) {

    // Get error code from exception
    def jsonSlurper = new JsonSlurper()
    def errorJson = jsonSlurper.parseText(ex.getMessage())
    def errorCode = errorJson.code ?: "Uknown error code"

    // Check if the error code is 408
    if (errorCode == 408) {
      echo "ERROR: Request timeout (408) when publishing to CosmosDB: ${ex}"
      // Retry up to 9 times with exponential backoff
      def maxRetries = 9
      def retryCount = 0
      def success = false
      
      while (retryCount < maxRetries && !success) {
      retryCount++
      def waitTime = Math.pow(2, retryCount) * 1000 // Exponential backoff in milliseconds
      echo "Retry attempt ${retryCount}/${maxRetries} after ${waitTime}ms delay"
      sleep(waitTime)
      
      try {
        publishToCosmosDb(params, Gatling.COSMOSDB_CONTAINER, reportsPath, '**/*.json')
        success = true
        echo "Successfully published to CosmosDB on retry ${retryCount}"
      } catch (Exception retryEx) {
        echo "Retry ${retryCount} failed: ${retryEx}"
        if (retryCount == maxRetries) {
        echo "All ${maxRetries} retries exhausted. Final failure: ${retryEx}"
        }
      }
      }
    } else {
      echo "ERROR: Failed to publish to CosmosDB: ${ex}"
    }
  }
}
