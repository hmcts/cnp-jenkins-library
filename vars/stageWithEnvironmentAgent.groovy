/**
 * Stage with environment-specific VM agent selection.
 */
def call(String name, String product, String environment, Closure body) {
  stage(name) {
    withEnvironmentAgent(environment, product) {
      withDockerAgent(product, body)
    }
  }
}
