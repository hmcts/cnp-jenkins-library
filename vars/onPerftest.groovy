import uk.gov.hmcts.contino.ProjectBranch

/**
 * onPerftest
 *
 * Runs the block of code if the current branch is 'perftest'
 *
 * onPerftest {
 *   ...
 * }
 */
def call(block) {
  if (new ProjectBranch(env.BRANCH_NAME).isPerftest()) {
    return block.call()
  }
}
