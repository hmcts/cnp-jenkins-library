import groovy.json.JsonOutput
import uk.gov.hmcts.contino.CosmosDbTargetResolver

def call(List bindings, Closure body) {
  recordCredentialUsage(credentialIds(bindings))
  steps.withCredentials(bindings, body)
}

def call(Map arguments, Closure body) {
  recordCredentialUsage(credentialIds(arguments.bindings ?: []))
  steps.withCredentials(arguments, body)
}

private List<String> credentialIds(Collection bindings) {
  bindings.collect { binding ->
    binding instanceof Map ? binding.credentialsId : binding.properties?.credentialsId
  }.findAll { it }.collect { it.toString() }
}

private void recordCredentialUsage(List<String> credentialIds) {
  def database = new CosmosDbTargetResolver(this).databaseName()

  credentialIds.each { credentialId ->
    println "Recording usage and performing telemetry or whatever for credential: ${credentialId}"
    // try {
    //   azureCosmosDBCreateDocument(
    //     container: 'credential-usage',
    //     credentialsId: 'cosmos-connection',
    //     database: database,
    //     document: JsonOutput.toJson([
    //       id           : UUID.randomUUID().toString(),
    //       credential_id: credentialId,
    //       job_name     : env.JOB_NAME,
    //       build_number : env.BUILD_NUMBER,
    //       recorded_at  : new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone('UTC'))
    //     ])
    //   )
    // } catch (Exception exception) {
    //   echo "Unable to record credential usage for '${credentialId}': ${exception.message}"
    // }
  }
}