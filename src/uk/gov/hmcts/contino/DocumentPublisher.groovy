package uk.gov.hmcts.contino

import com.microsoft.azure.documentdb.Document
import com.microsoft.azure.documentdb.DocumentClient
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

class DocumentPublisher {

  def steps
  def product
  def component
  def environment
  def env
  def jsonSlurper = new JsonSlurper()

  DocumentPublisher(steps, product, component, environment) {
    this.steps = steps
    this.product = product
    this.component = component
    this.environment = environment
    this.env = steps.env
  }

  void publish(DocumentClient documentClient, String collectionLink, Object data) {
      Document documentDefinition = new Document(data)
      documentClient.createDocument(collectionLink, documentDefinition, null, false)
  }

  void publishAll(DocumentClient documentClient, String collectionLink, String basedir, String pattern) {
    List files = findFiles(basedir, pattern)

    files.each {
      def jsonObject = wrapWithBuildInfo(new File(it))
      this.publish(documentClient, collectionLink, jsonObject)
    }
  }

  String wrapWithBuildInfo(File file) {
    Map buildInfo = getBuildInfo()
    buildInfo.put(file.getName(), fileToJson(file))
    JsonOutput.toJson(buildInfo).toString()
  }

  Object fileToJson(File filePath) {
    this.jsonSlurper.parse(filePath)
  }

  static List findFiles(String basedir, String pattern) {
    new FileNameFinder().getFileNames(basedir, pattern)
  }

  Map getBuildInfo() {
     [
      product                      : product,
      component                    : component,
      environment                  : environment,
      branch_name                  : env.BRANCH_NAME,
      build_number                 : env.BUILD_NUMBER,
      build_id                     : env.BUILD_ID,
      build_display_name           : env.BUILD_DISPLAY_NAME,
      job_name                     : env.JOB_NAME,
      job_base_name                : env.JOB_BASE_NAME,
      build_tag                    : env.BUILD_TAG,
      node_name                    : env.NODE_NAME
    ]
  }

}
