import uk.gov.hmcts.contino.DocumentPublisher

def call(steps, params, containerName, baseDir, pattern) {
  def documentPublisher = new DocumentPublisher(steps, params)
  documentPublisher.publishAll(containerName, baseDir, pattern)
}
