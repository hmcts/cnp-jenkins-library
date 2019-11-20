import uk.gov.hmcts.contino.ProjectBranch

/**
 * onEthosldata
 *
 * Runs the block of code if the current branch is 'ethosldata'
 *
 * onEthosldata {
 *   ...
 * }
 */
def call(block) {
  if (new ProjectBranch(env.BRANCH_NAME).isEthosldata()) {
    return block.call()
  }
}
