import uk.gov.hmcts.pipeline.TerraformInfraApprovals
import uk.gov.hmcts.contino.MetricsPublisher

/**
 * approvedTerraformInfrastructure(environment)
 *
 * Runs the block of code if the current Terffaorm infrastructure is allowed to deploy to the environment
 *
 * approvedTerraformInfrastructure(environment) {
 *   ...
 * }
 */
def call(String environment, MetricsPublisher metricsPublisher, Closure block) {
  if (!new TerraformInfraApprovals(this).isApproved(".")) {//, environment, env.GIT_URL)) {
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
  }
  return block.call()
}
