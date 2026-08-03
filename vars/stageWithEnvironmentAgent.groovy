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

/** Variant that skips workspace stash/unstash — use when no workspace files are needed. */
def call(String name, String product, String environment, boolean skipStash, Closure body) {
  stage(name) {
    withEnvironmentAgent(environment, product, skipStash) {
      withDockerAgent(product, body)
    }
  }
}
