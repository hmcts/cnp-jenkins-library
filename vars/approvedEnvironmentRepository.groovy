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
  if (!new EnvironmentApprovals(this).isApproved(environment, env.GIT_URL)) {
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
Repo ${env.GIT_URL} is not approved for environment '${environment}'"
================================================================================
"""

  }
  return block.call()
}
