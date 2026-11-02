import uk.gov.hmcts.pipeline.deprecation.WarningCollector
import uk.gov.hmcts.pipeline.DeprecationConfig

import java.time.LocalDate

def call(String repoUrl = null) {

  def moduleDeprecationConfig = repoUrl ?
      new DeprecationConfig(this).getDeprecationConfig(repoUrl).terraform_modules :
      new DeprecationConfig(this).getDeprecationConfig().terraform_modules

  if (!moduleDeprecationConfig) {
    return
  }

  writeFile file: 'check-module-branches.sh', text: libraryResource('uk/gov/hmcts/infrastructure/check-module-branches.sh')

  try {
    moduleDeprecationConfig.each { moduleName, deprecation ->
      def patterns = []
      if (deprecation.pattern instanceof Collection) {
        patterns.addAll(deprecation.pattern)
      } else {
        patterns.addAll(
          deprecation.pattern
            .toString()
            .split(/\|/)
            .collect { it.trim() }
            .findAll { it }
        )
      }

      patterns.each { pattern ->
        int status = sh(
          script: """
          chmod +x check-module-branches.sh
          ./check-module-branches.sh '${moduleName}' '${pattern}' '${deprecation.version}' '${deprecation.date_deadline}'
          """,
          returnStatus: true
        )

        if (status != 0) {
          WarningCollector.addPipelineWarning(
            "deprecated_module_branch",
            "Your terraform code pins the `${moduleName}` module to the deprecated `${pattern}` branch. This branch is being merged back to `${deprecation.version}` - please update your module source to use `?ref=${deprecation.version}`.",
            LocalDate.parse(deprecation.date_deadline)
          )
        }
      }
    }
  } finally {
    sh 'rm -f check-module-branches.sh'
  }
}
