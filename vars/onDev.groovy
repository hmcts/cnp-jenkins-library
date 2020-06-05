import uk.gov.hmcts.contino.ProjectBranch

/**
 * onDev
 *
 * Runs the block of code if the current branch is dev
 *
 * onDev {
 *   ...
 * }
 */
def call(block) {
  if (new ProjectBranch(env.BRANCH_NAME).isDev()) {
    return block.call()
  }
}
