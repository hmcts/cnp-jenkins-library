/**
 * Run a closure inside a specific container of a kubernetes agent pod (or skip container altogether)
 */
def call(String product, Closure body) {
  String agentContainer = env.BUILD_AGENT_CONTAINER
  if (agentContainer != null && agentContainer != "") {
    echo "Using agent container: ${agentContainer}"
    try {
      container(agentContainer) {
        dockerAgentSetup(product)
        body()
      }
    } catch (Exception e) {
      containerLog agentContainer
      throw e
    }
  } else {
    body()
  }
}
