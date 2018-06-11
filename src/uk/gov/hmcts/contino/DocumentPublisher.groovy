package uk.gov.hmcts.contino

import com.microsoft.azure.documentdb.Document
import com.microsoft.azure.documentdb.DocumentClient
import groovy.json.JsonSlurper

class DocumentPublisher {

  def jsonSlurper = new JsonSlurper()

  def publish(DocumentClient documentClient, String collectionLink, Object data) {
    try {
      Document documentDefinition = new Document(data)
      documentClient.createDocument(collectionLink, documentDefinition, null, false)
    }
    finally {
      documentClient.close()
    }
  }

  def publishAll(DocumentClient documentClient, String collectionLink, String basedir, String pattern) {
    def files = this.findFiles(basedir, pattern)

    files.each {
      def jsonObject = toJson(it)
      this.publish(documentClient, collectionLink, jsonObject)
    }
  }

  def toJson(String filePath) {
    this.jsonSlurper.parse(new java.io.File(filePath))
  }

  def findFiles(String basedir, String pattern) {
    new FileNameFinder().getFileNames(basedir, pattern)
  }
}
