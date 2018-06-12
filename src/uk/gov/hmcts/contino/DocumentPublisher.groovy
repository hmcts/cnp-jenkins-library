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

  DocumentPublisher(steps, params) {
    this.steps = steps
    this.params = params
    this.env = steps.env
  }

  void publishAll(String collectionLink, String baseDir, String pattern) {

    def files = findFiles(baseDir, pattern)
    List documents = new ArrayList()

    files.each {
      def fullPath = "${baseDir}/" + it.path
      def json = this.steps.readJSON file: fullPath
      def jsonObject = wrapWithBuildInfo(it.name, json)
      documents.add(jsonObject)
    }

    publish(collectionLink, documents)
  }

  @NonCPS
  private def publish(collectionLink, documents) {

    def documentClient = new DocumentClient(env.COSMOSDB_URL, env.COSMOSDB_TOKEN_KEY, null, null)

    try {
      documents.each {
        documentClient.createDocument(collectionLink, new Document(it), null, false)
      }
    }
    finally {
      documentClient.close()
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
