package uk.gov.hmcts.pipeline

class CVEPublisher {

  def steps
  private final boolean ignoreErrors

  CVEPublisher(steps) {
    this(steps, true)
  }

  CVEPublisher(steps, ignoreErrors) {
    this.steps = steps
    this.ignoreErrors = ignoreErrors
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
      steps.echo "Publishing CVE report"
      def summary = [
        id    : UUID.randomUUID().toString(),
        build : [
          branch_name       : steps.env.BRANCH_NAME,
          build_display_name: steps.env.BUILD_DISPLAY_NAME,
          build_tag         : steps.env.BUILD_TAG,
          git_url           : steps.env.GIT_URL,
          codebase_type     : codeBaseType
        ],
        report: report
      ]

      steps.azureCosmosDBCreateDocument(container: 'cve-reports', credentialsId: 'cosmos-connection', database: 'jenkins', document: summary)
    } catch (err) {
      if (ignoreErrors) {
        steps.echo "Unable to publish CVE report '${err}'"
      } else {
        throw err
      }
    }
  }
}
