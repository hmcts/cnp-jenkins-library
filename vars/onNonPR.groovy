import uk.gov.hmcts.contino.ProjectBranch

/**
 * onNonPR
 *
 * Runs the block of code if the current branch is not associated with a pull request
 *
 * onNonPR {
 *   ...
 * }
 */
def call(block) {
  if (!new ProjectBranch(env.BRANCH_NAME).isPR()) {
    return block.call()
  }
}
