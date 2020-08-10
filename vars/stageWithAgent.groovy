/**
 * Stage with Agent selection
 *
 */
def call(String name, String product, Closure body) {
  stage(name) {
    withDockerAgent(product, body)
  }
}
