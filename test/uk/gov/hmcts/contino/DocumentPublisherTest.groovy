package uk.gov.hmcts.contino

import com.microsoft.azure.documentdb.DocumentClient
import spock.lang.Specification

class DocumentPublisherTest extends Specification {

  private static final String COLLECTION_LINK = "dbs/jenkins/colls/mycollection"
  private static final String DATA = '{ \"key\": \"value\"}'

  def documentPublisher
  def documentClient = Mock(DocumentClient)

  def setup() {
    documentPublisher = new DocumentPublisher('url', 'key')
    documentPublisher.documentClient = documentClient
  }

  def "Publish"() {
    when:
      documentPublisher.publish(COLLECTION_LINK, DATA)
    then:
      1 * documentClient.createDocument(COLLECTION_LINK, DATA)

  }

  def "PublishAll"() {
  }

}
