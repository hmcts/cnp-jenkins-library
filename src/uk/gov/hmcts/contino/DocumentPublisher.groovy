package uk.gov.hmcts.contino

import com.microsoft.azure.documentdb.Document
import com.microsoft.azure.documentdb.DocumentClient
import groovy.json.JsonSlurper

class DocumentPublisher {

  def documentClient
  def jsonSlurper = new JsonSlurper()

  DocumentPublisher(String dbUrl, String dbKey) {
    this.documentClient = new DocumentClient(dbUrl, dbKey, null, null)
  }

  def publish(String collectionLink, Object data) {
    Document documentDefinition = new Document(data)
    this.documentClient.createDocument(collectionLink, documentDefinition, null, false)
  }

  def publishAll(String collectionLink, String basedir, String pattern) {
    def files = this.findFiles(basedir, pattern)

    files.each { -> jsonFilePath
      def jsonObject = toJson(jsonFilePath)
      publish(collectionLink, jsonObject)
    }
  }

  def toJson(String filePath) {
    this.jsonSlurper.parse(new java.io.File(filePath))
  }

  def findFiles(String basedir, String pattern) {
    new FileNameFinder().getFileNames(basedir, pattern)
  }
}
