import uk.gov.hmcts.contino.ProjectBranch

/**
 * onHMCTSDemo
 *
 * Runs the block of code if the current branch is demo
 *
 * onHMCTSDemo {
 *   ...
 * }
 */
def call(block) {
  if (new ProjectBranch(env.BRANCH_NAME).isHMCTSDemo()) {
    return block.call()
  }
}
