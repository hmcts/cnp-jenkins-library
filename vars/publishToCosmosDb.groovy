import uk.gov.hmcts.contino.DocumentPublisher

def call(params, containerName, baseDir, pattern) {
  def documentPublisher = new DocumentPublisher(this, params)
  documentPublisher.publishAll(containerName, baseDir, pattern)
}
