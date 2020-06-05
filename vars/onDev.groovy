import uk.gov.hmcts.contino.ProjectBranch

/**
 * onDemo
 *
 * Runs the block of code if the current branch is demo
 *
 * onDemo {
 *   ...
 * }
 */
def call(block) {
  if (new ProjectBranch(env.BRANCH_NAME).isDev()) {
    return block.call()
  }
}