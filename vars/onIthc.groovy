import uk.gov.hmcts.contino.ProjectBranch

/**
 * onIthc
 *
 * Runs the block of code if the current branch is 'ithc'
 *
 * onIthc {
 *   ...
 * }
 */
def call(block) {
  if (new ProjectBranch(env.BRANCH_NAME).isIthc()) {
    return block.call()
  }
}
