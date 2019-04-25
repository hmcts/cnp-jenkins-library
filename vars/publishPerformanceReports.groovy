import uk.gov.hmcts.contino.Gatling

def call(steps, params)  {
  azureBlobUpload('buildlog-storage-account', Gatling.reportsDir, "performance/${params.product}-${params.component}/${params.environment}")
  def reportsPath = "${WORKSPACE}/" + Gatling.reportsPath
  publishToCosmosDb(steps, params, Gatling.COSMOSDB_COLLECTION, reportsPath, '**/*.json')
}
