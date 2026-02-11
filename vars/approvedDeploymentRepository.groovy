import uk.gov.hmcts.pipeline.DeploymentControls
import uk.gov.hmcts.contino.MetricsPublisher

/**
 * approvedDeploymentRepository()
 *
 * Runs the block of code if the current repo is allowed to deploy
 *
 * approvedDeploymentRepository() {
 *   ...
 * }
 */
def call(metricsPublisher, Closure block) {
  if (!new DeploymentControls(this).isDeployEnabled(env.GIT_URL)) {
    stage('Deployment Not Enabled') {
      echo '''
================================================================================

 ____      ____  _       _______     ____  _____  _____  ____  _____   ______
|_  _|    |_  _|/ \\     |_   __ \\   |_   \\|_   _||_   _||_   \\|_   _|.' ___  |
  \\ \\  /\\  / / / _ \\      | |__) |    |   \\ | |    | |    |   \\ | | / .'   \\_|
   \\ \\/  \\/ / / ___ \\     |  __ /     | |\\ \\| |    | |    | |\\ \\| | | |   ____
    \\  /\\  /_/ /   \\ \\_  _| |  \\ \\_  _| |_\\   |_  _| |_  _| |_\\   |_\\ `.___]  |
     \\/  \\/|____| |____||____| |_____|\\____||_____||_____|\\____|`._____.'
'''

      echo """
Repo ${env.GIT_URL} is only allowed to run build stages.
Deployment stages are disabled for this repository.
================================================================================
"""
      metricsPublisher.publish("deployment-not-enabled")
    }
    return null
  } else {
    return block.call()
  }
}
