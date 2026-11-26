import groovy.json.JsonOutput
import uk.gov.hmcts.contino.CosmosDbTargetResolver

def call(String credentialId) {
  recordCredentialUsage(credentialId)
  steps.credentials(credentialId)
}

private void recordCredentialUsage(String credentialId) {
  try {
    println "Recording usage and performing telemetry or whatever for credential: ${credentialId}"
    // azureCosmosDBCreateDocument(
    //   container: 'credential-usage',
    //   credentialsId: 'cosmos-connection',
    //   database: new CosmosDbTargetResolver(this).databaseName(),
    //   document: JsonOutput.toJson([
    //     id           : UUID.randomUUID().toString(),
    //     credential_id: credentialId,
    //     job_name     : env.JOB_NAME,
    //     build_number : env.BUILD_NUMBER,
    //     recorded_at  : new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone('UTC'))
    //   ])
    // )
  } catch (Exception exception) {
    echo "Unable to record credential usage for '${credentialId}': ${exception.message}"
  }
}