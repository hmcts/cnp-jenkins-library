/*
 * onMaster
 *
 * Runs the block of code if the current branch is master
 *
 * onMaster {
 *   ...
 * }
 */
def call(block) {
  if (env.BRANCH_NAME == 'master') {
    return block.call()
  }
}
