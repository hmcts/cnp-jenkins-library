package uk.gov.hmcts.contino

@Grab('com.microsoft.azure:azure-documentdb:1.15.2')
import com.microsoft.azure.documentdb.Document
import com.microsoft.azure.documentdb.DocumentClient
import groovy.json.JsonOutput
import com.cloudbees.groovy.cps.NonCPS

class DocumentPublisher implements Serializable {

  def steps
  def params
  def env
  def cosmosDbUrl

  DocumentPublisher(steps, params) {
    this.steps = steps
    this.params = params
    this.env = steps.env

    def tmpCosmosDbUrl = this.env.PIPELINE_METRICS_URL
    if (tmpCosmosDbUrl?.trim()) {
      this.cosmosDbUrl = tmpCosmosDbUrl
    } else {
      this.cosmosDbUrl = params.subscription == 'sandbox' ?
        'https://sandbox-pipeline-metrics.documents.azure.com/' :
        'https://pipeline-metrics.documents.azure.com/'
    }
  }

  void publishAll(String collectionLink, String baseDir, String pattern) {

    def files = findFiles(baseDir, pattern)
    List documents = new ArrayList()

    files.each {
      def absolutePath = "${baseDir}/" + it.path
      def json = this.steps.readJSON file: absolutePath
      documents.add(wrapWithBuildInfo(it.name, json))
    }

    publish(getCosmosDbKey(), collectionLink, documents)
  }

  @NonCPS
  private def publish(cosmosDbKey, collectionLink, documents) {

    def documentClient = new DocumentClient(cosmosDbUrl, cosmosDbKey, null, null)

    try {
      documents.each {
        documentClient.createDocument(collectionLink, new Document(it), null, false)
      }
    }
    finally {
      documentClient.close()
    }
  }

  def getCosmosDbKey() {
    steps.withCredentials([[$class: 'StringBinding', credentialsId: 'COSMOSDB_TOKEN_KEY', variable: 'COSMOSDB_TOKEN_KEY']]) {
      return env.COSMOSDB_TOKEN_KEY
    }
  }

  String wrapWithBuildInfo(fileName, json) {
    Map buildInfo = getBuildInfo()
    buildInfo.put(fileName, json)
    JsonOutput.toJson(buildInfo).toString()
  }

  def findFiles(String baseDir, String pattern) {
    steps.dir(baseDir) {
      steps.findFiles(glob: pattern)
    }
  }

  Map getBuildInfo() {
    [
      product                      : params.product,
      component                    : params.component,
      environment                  : params.environment,
      branch_name                  : env.BRANCH_NAME,
      build_number                 : env.BUILD_NUMBER,
      build_id                     : env.BUILD_ID,
      build_display_name           : env.BUILD_DISPLAY_NAME,
      job_name                     : env.JOB_NAME,
      job_base_name                : env.JOB_BASE_NAME,
      build_tag                    : env.BUILD_TAG,
      node_name                    : env.NODE_NAME,
      stage_timestamp              : new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone("UTC"))
    ]
  }

}
