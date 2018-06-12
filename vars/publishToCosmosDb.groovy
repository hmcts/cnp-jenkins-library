import com.microsoft.azure.documentdb.DocumentClient
import uk.gov.hmcts.contino.DocumentPublisher

def call(steps, params) {

  def env = steps.env
  def documentClient = new DocumentClient(env.COSMOSDB_URL, env.COSMOSDB_TOKEN_KEY, null, null)
  def documentPublisher = new DocumentPublisher(steps, params.product, params.component, params.environment)

  try {
    documentPublisher.publishAll(documentClient, "dbs/jenkins/colls/performance-metrics", "**/*.json")
  }
  finally {
    documentClient.close()
  }

}
