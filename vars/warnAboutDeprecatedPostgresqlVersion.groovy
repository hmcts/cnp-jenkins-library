import uk.gov.hmcts.pipeline.DeprecationConfig
import uk.gov.hmcts.pipeline.deprecation.WarningCollector

import java.time.LocalDate

def call() {
  def deprecation = new DeprecationConfig(this)
    .getDeprecationConfig(env.GIT_URL)
    .database.postgresql

  writeFile file: 'check-deprecated-postgresql-version.sh',
    text: libraryResource('uk/gov/hmcts/infrastructure/check-deprecated-postgresql-version.sh')

  try {
    sh 'chmod +x check-deprecated-postgresql-version.sh'
    def result = sh(
      script: "./check-deprecated-postgresql-version.sh '${deprecation.version}' tfplan",
      returnStdout: true
    ).trim()

    if (result) {
      WarningCollector.addPipelineWarning(
        'deprecated_postgresql_server_version',
        deprecation.message,
        LocalDate.parse(deprecation.date_deadline)
      )
    }
  } finally {
    sh 'rm -f check-deprecated-postgresql-version.sh'
  }
}
