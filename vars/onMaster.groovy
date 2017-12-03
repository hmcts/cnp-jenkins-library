import uk.gov.hmcts.contino.ProjectBranch

/**
 * onMaster
 *
 * Runs the block of code if the current branch is master
 *
 * onMaster {
 *   ...
 * }
 */
def call(block) {
  if (new ProjectBranch(env.BRANCH_NAME).isMaster()) {
    return block.call()
  }
}
