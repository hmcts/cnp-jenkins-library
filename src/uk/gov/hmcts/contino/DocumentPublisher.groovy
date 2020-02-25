package uk.gov.hmcts.contino

import groovy.json.JsonOutput
import com.cloudbees.groovy.cps.NonCPS

import static uk.gov.hmcts.contino.MetricsPublisher.METRICS_RESOURCE_PATH

class DocumentPublisher implements Serializable {

  private static final String DB_DEFAULT_URL = 'https://pipeline-metrics.documents.azure.com/'
  private static final String DB_SANDBOX_URL = 'https://sandbox-pipeline-metrics.documents.azure.com/'

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
      def absolutePath = "${baseDir}/" + it.path
      def json = this.steps.readJSON file: absolutePath
      documents.add(wrapWithBuildInfo(it.name, json))
    }

    publish(getCosmosDbKey(), collectionLink, documents)
  }

  private def publish(cosmosDbKey, collectionLink, documents) {
    def cosmosDbUrl = params.subscription == 'sandbox' ? DB_SANDBOX_URL : DB_DEFAULT_URL
    def collection = collectionLink.split('/').last()

    steps.sh "mkdir -p /tmp/metrics-reporting"
    steps.writeFile(file: '/tmp/metrics-reporting/package.json', text: steps.libraryResource("${METRICS_RESOURCE_PATH}/package.json"))
    steps.writeFile(file: '/tmp/metrics-reporting/yarn.lock', text: steps.libraryResource("${METRICS_RESOURCE_PATH}/yarn.lock"))
    steps.writeFile(file: '/tmp/metrics-reporting/metrics-publisher.js', text: steps.libraryResource("${METRICS_RESOURCE_PATH}/metrics-publisher.js"))

    documents.each {
      steps.sh """
      cd /tmp/metrics-reporting/
      chmod +x metrics-publisher.js
      yarn install

      export COSMOS_DB_URL=${cosmosDbUrl}
      export COSMOSDB_TOKEN_KEY=${cosmosDbKey}
      export COSMOS_COLLECTION_ID=${collection}

      ./metrics-publisher.js '${it}'
    """
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
