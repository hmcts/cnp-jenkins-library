package uk.gov.hmcts.contino

import com.microsoft.azure.documentdb.Document
import com.microsoft.azure.documentdb.DocumentClient
import spock.lang.Specification

class DocumentPublisherTest extends Specification {

  private static final String COLLECTION_LINK = "dbs/jenkins/colls/mycollection"
  private static final String DATA = '{ \"key\": \"value\"}'

  def documentPublisher
  def documentClient

  def setup() {
    documentClient = Mock(DocumentClient)
    documentPublisher = new DocumentPublisher()
  }

  def "publish single document"() {
    when:
      documentPublisher.publish(this.documentClient, COLLECTION_LINK, DATA)
    then:
      1 * documentClient.createDocument(COLLECTION_LINK, _ as Document, null, false)
      1 * documentClient.close()
  }

  def "publish all documents"() {
    when:
      documentPublisher.publishAll(this.documentClient, COLLECTION_LINK, 'testResources/files/perf-reports', '**/*.json')
    then:
      2 * documentClient.createDocument(COLLECTION_LINK, _ as Document, null, false)

  }

  def "get files of pattern **/*.json"() {
    when:
      def files = documentPublisher.findFiles('testResources/files/perf-reports', '**/*.json')

    then:
      files.size() == 2
  }

}
