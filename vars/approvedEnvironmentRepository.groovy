import uk.gov.hmcts.pipeline.EnvironmentApprovals

/**
 * approvedEnvironmentRepository(environment)
 *
 * Runs the block of code if the current repo is allowed to deploy to the environment
 *
 * approvedEnvironmentRepository(environment) {
 *   ...
 * }
 */
def call(String environment, Closure block) {
  if (new EnvironmentApprovals(this).isApproved(environment, env.GIT_URL)) {
    return block.call()
  } else {
    echo "Repo ${env.GIT_URL} is not approved for environment ${environment}"
  }
}
