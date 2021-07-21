package uk.gov.hmcts.pipeline

@Grab('com.microsoft.azure:azure-documentdb:1.15.2')
import com.cloudbees.groovy.cps.NonCPS
import com.microsoft.azure.documentdb.Document
import com.microsoft.azure.documentdb.DocumentClient
import groovy.json.JsonOutput
import uk.gov.hmcts.contino.Subscription

class CVEPublisher {

  private static final String COSMOS_COLLECTION_LINK = 'dbs/jenkins/colls/cve-reports'
  def steps
  def documentClient
  private final boolean ignoreErrors

  CVEPublisher(steps, DocumentClient documentClient) {
    this(
      steps,
      true,
      documentClient
    )
  }

  CVEPublisher(steps, ignoreErrors, DocumentClient documentClient) {
    this.ignoreErrors = ignoreErrors
    this.steps = steps
    this.documentClient = documentClient
  }

  static CVEPublisher create(steps) {
    Subscription subscription = new Subscription(steps.env)

    def cosmosDbUrl = steps.env.PIPELINE_METRICS_URL
    if (!cosmosDbUrl?.trim()) {
      cosmosDbUrl = subscription.nonProdName == 'sandbox' ?
        'https://sandbox-pipeline-metrics.documents.azure.com/' :
        'https://pipeline-metrics.documents.azure.com/'
    }

    steps.withCredentials([[$class: 'StringBinding', credentialsId: 'COSMOSDB_TOKEN_KEY', variable: 'COSMOSDB_TOKEN_KEY']]) {
      new CVEPublisher(steps, steps.env.COSMOSDB_TOKEN_KEY != null ?
        new DocumentClient(cosmosDbUrl, steps.env.COSMOSDB_TOKEN_KEY, null, null)
        : null)
    }
  }

  /**
   * Publishes a report to CosmosDB
   * There's a limit of 2MB per document in CosmosDB so you may need to filter your
   * report to remove fields.
   *
   * @param codeBaseType string indicating which type of report it is, e.g. java or node
   * @param report provider specific report should be a groovy object that can be converted to json.
   */
  def publishCVEReport(String codeBaseType, report) {
    try {
      if (documentClient == null) {
        steps.echo "Set the 'COSMOSDB_TOKEN_KEY' environment variable to enable metrics publishing"
        return
      }

      steps.echo "Publishing CVE report"
      def summary = JsonOutput.toJson([
        build: [
          branch_name                  : steps.env.BRANCH_NAME,
          build_display_name           : steps.env.BUILD_DISPLAY_NAME,
          build_tag                    : steps.env.BUILD_TAG,
          git_url                      : steps.env.GIT_URL,
          codebase_type                : codeBaseType
        ],
        report: report
      ])

      createDocument(summary)
    } catch (err) {
      if (ignoreErrors) {
        steps.echo "Unable to publish CVE report '${err}'"
      } else {
        throw err
      }
    }
  }


  @NonCPS
  private def createDocument(String reportJSON) {
    try {
      documentClient.createDocument(COSMOS_COLLECTION_LINK, new Document(reportJSON)
        , null, false)
    } finally {
      documentClient.close()
    }
  }
}
