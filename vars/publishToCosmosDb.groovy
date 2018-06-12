import com.timw.DocumentPublisher

def call(steps, params, baseDir, pattern) {
  def documentPublisher = new DocumentPublisher(steps, params)
  documentPublisher.publishAll('dbs/jenkins/colls/performance-metrics', baseDir, pattern)
}
