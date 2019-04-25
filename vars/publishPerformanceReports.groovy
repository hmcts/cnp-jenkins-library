import uk.gov.hmcts.contino.Gatling

def call(steps, params)  {
  azureBlobUpload('buildlog-storage-account', steps.env.GATLING_REPORTS_DIR, "performance/${params.product}-${params.component}/${params.environment}")
  def reportsPath = "${WORKSPACE}/" + steps.env.GATLING_REPORTS_PATH
  publishToCosmosDb(steps, params, Gatling.COSMOSDB_COLLECTION, reportsPath, '**/*.json')
}
