import uk.gov.hmcts.pipeline.TerraformInfraApprovals
import uk.gov.hmcts.contino.MetricsPublisher
import uk.gov.hmcts.pipeline.deprecation.WarningCollector

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
  TerraformInfraApprovals approvals = new TerraformInfraApprovals(this)
  if (!approvals.isApproved(".")) {
    def results = approvals.getResults(".")
    echo results + ""
    def file = readFile("terraform-approvals.log")
    echo file

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
    WarningCollector.addPipelineWarning("deprecated_not_approved_infra", """
this repo is using a terraform resource that is not allowed, 
whitelists are stored in https://github.com/hmcts/cnp-jenkins-config/tree/master/terraform-infra-approvals 
send a pull request if you think this is in error. 
non whitelisted resources:
${results}
"""
      , new Date().parse("dd.MM.yyyy", "05.09.2019"))
  } else {
    log.info("All infrastructure is approved")
  }
  return block.call()
}
