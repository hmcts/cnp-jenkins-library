import uk.gov.hmcts.pipeline.TerraformInfraApprovals
import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.pipeline.deprecation.WarningCollector

import java.time.LocalDate

/**
 * approvedTerraformInfrastructure(environment)
 *
 * Runs the block of code if the current Terffaorm infrastructure is allowed to deploy to the environment
 *
 * approvedTerraformInfrastructure(environment) {
 *   ...
 * }
 */
def call(String environment, String product, MetricsPublisher metricsPublisher, Closure block) {
  withDockerAgent(product) {
    TerraformInfraApprovals approvals = new TerraformInfraApprovals(this)
    if (!approvals.isApproved(".")) {
      // we run the tf-utils again because getting it the stderr through jenkins is a nightmare
      // returnStdout will fail if non 0 result, set +ex doesn't stop it failing
      // tee by default only gets stdout
      // I suspect that with a proper bash shell it would be easier, but we're using a busybox sh shell here
      approvals.storeResults(".")
      def results = readFile("terraform-approvals.log")
      echo results

      echo '''
================================================================================

 ____      ____  _       _______     ____  _____  _____  ____  _____   ______
|_  _|    |_  _|/ \\     |_   __ \\   |_   \\|_   _||_   _||_   \\|_   _|.' ___  |
  \\ \\  /\\  / / / _ \\      | |__) |    |   \\ | |    | |    |   \\ | | / .'   \\_|
   \\ \\/  \\/ / / ___ \\     |  __ /     | |\\ \\| |    | |    | |\\ \\| | | |   ____
    \\  /\\  /_/ /   \\ \\_  _| |  \\ \\_  _| |_\\   |_  _| |_  _| |_\\   |_\\ `.___]  |
     \\/  \\/|____| |____||____| |___||_____|\\____||_____||_____|\\____|`._____.'
'''

      echo """
Infrastructure for repo ${env.GIT_URL} is not approved for environment '${environment}'"
================================================================================
"""

      metricsPublisher.publish("not-approved-infra")

      def repo = env.JENKINS_CONFIG_REPO ?: "cnp-jenkins-config"

      WarningCollector.addPipelineWarning("deprecated_not_approved_infra", """
this repo is using a terraform resource that is not allowed,
whitelists are stored in https://github.com/hmcts/${repo}/tree/master/terraform-infra-approvals
send a pull request if you think this is in error.
non whitelisted resources:
```
${results.replace("Error matching resources: ", "")}
```
"""
        , LocalDate.of(2019, 9, 24))
    } else {
      log.info("All infrastructure is approved")
    }
    return block.call()
  }
}
