def call(steps, params)  {
  azureBlobUpload('buildlog-storage-account', Gatling.GATLING_REPORTS_DIR, "performance/${product}-${component}/${environment}")
  def reportsPath = "${WORKSPACE}/" + Gatling.GATLING_REPORTS_PATH
  publishToCosmosDb(steps, params, Gatling.COSMOSDB_COLLECTION, reportsPath, '**/*.json')
}
