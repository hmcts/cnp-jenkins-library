import uk.gov.hmcts.contino.ProjectBranch

/**
 * onPathToLive
 *
 * Runs the block of code if the current branch is associated with a pull request or master
 *
 * onPR {
 *   ...
 * }
 */
def call(block) {
  def branch = new ProjectBranch(env.BRANCH_NAME)
  if (branch.isPR() || branch.isMaster()) {
    return block.call()
  }
}
