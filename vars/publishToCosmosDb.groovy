import uk.gov.hmcts.contino.DocumentPublisher

def call(steps, params, collectionLink, baseDir, pattern) {
  def documentPublisher = new DocumentPublisher(steps, params)
  documentPublisher.publishAll(collectionLink, baseDir, pattern)
}
