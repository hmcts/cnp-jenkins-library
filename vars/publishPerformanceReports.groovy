import uk.gov.hmcts.contino.Gatling

def call(params)  {
  try {
      azureBlobUpload('buildlog-storage-account', env.GATLING_REPORTS_PATH, "performance/${params.product}-${params.component}/${params.environment}")
  }
  catch (Exception ex) {
    echo "ERROR: Failed to upload performance reports to blob storage destination performance/${params.product}-${params.component}/${params.environment} " +
      "due to the following error: ${ex}"
  }
  def reportsPath = env.GATLING_REPORTS_PATH
  publishToCosmosDb(params, Gatling.COSMOSDB_CONTAINER, reportsPath, '**/*.json')
}
