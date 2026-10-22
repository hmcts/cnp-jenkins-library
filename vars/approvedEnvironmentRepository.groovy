import uk.gov.hmcts.pipeline.EnvironmentApprovals
import uk.gov.hmcts.pipeline.WarningBanner
import uk.gov.hmcts.contino.MetricsPublisher

/**
 * approvedEnvironmentRepository(environment)
 *
 * Runs the block of code if the current repo is allowed to deploy to the environment
 *
 * approvedEnvironmentRepository(environment) {
 *   ...
 * }
 */
def call(String environment, metricsPublisher, Closure block) {
  if (!new EnvironmentApprovals(this).isApproved(environment, env.GIT_URL)) {
    echo WarningBanner.get(this)

    echo """
Repo ${env.GIT_URL} is not approved for environment '${environment}'"
================================================================================
"""
    metricsPublisher.publish("not-approved-repo")
  } else {
    return block.call()
  }
}
