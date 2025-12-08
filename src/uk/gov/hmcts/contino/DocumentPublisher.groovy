package uk.gov.hmcts.contino

import groovy.json.JsonOutput

class DocumentPublisher implements Serializable {

  def steps
  def params
  def env
  CosmosDbTargetResolver cosmosDbTargetResolver

  DocumentPublisher(steps, params, CosmosDbTargetResolver cosmosDbTargetResolver = null) {
    this.steps = steps
    this.params = params
    this.env = steps.env
    this.cosmosDbTargetResolver = cosmosDbTargetResolver ?: new CosmosDbTargetResolver(steps)
  }

  void publishAll(String containerName, String baseDir, String pattern) {
    def files = findFiles(baseDir, pattern)
    List documents = new ArrayList()

    files.each {
      def absolutePath = "${baseDir}/" + it.path
      def json = this.steps.readJSON file: absolutePath
      documents.add(wrapWithBuildInfo(it.name, json))
    }

    publish(containerName, documents)
  }

  private def publish(containerName, documents) {
    def database = cosmosDbTargetResolver.databaseName()
    documents.each {
      steps.azureCosmosDBCreateDocument(container: containerName, credentialsId: 'cosmos-connection', database: database, document: it)
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
      id                           : UUID.randomUUID().toString(),
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
