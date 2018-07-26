import uk.gov.hmcts.contino.ProjectBranch

/**
 * onPR
 *
 * Runs the block of code if the current branch is associated with a pull request
 *
 * onPR {
 *   ...
 * }
 */
def call(block) {
  if (new ProjectBranch(env.BRANCH_NAME).isPR() && env.CHANGE_TITLE.startsWith('[PREVIEW]')) {
    return block.call()
  }
}
