import uk.gov.hmcts.contino.Gatling

def call(steps, params)  {
  try {
      azureBlobUpload('buildlog-storage-account', steps.env.GATLING_REPORTS_DIR, "performance/${params.product}-${params.component}/${params.environment}")
  }
  catch (Exception ex) {
    echo "ERROR: Failed to upload performance reports to blob storage destination performance/${params.product}-${params.component}/${params.environment} " +
      "due to the following error: ${ex}"
  }
  def reportsPath = "${WORKSPACE}/" + steps.env.GATLING_REPORTS_PATH
  publishToCosmosDb(steps, params, Gatling.COSMOSDB_CONTAINER, reportsPath, '**/*.json')
}
