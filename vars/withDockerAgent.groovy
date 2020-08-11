/**
 * Run a closure inside a specific container of a kubernetes agent pod (or skip container altogether)
 */
def call(String product, Closure body) {
  String agentContainer = env.BUILD_AGENT_CONTAINER
  if (agentContainer != null && agentContainer != "") {
    try {
      container(agentContainer) {
        echo "Using agent container: ${agentContainer}"
        dockerAgentSetup()
        body()
      }
    } catch (Exception e) {
      containerLog agentContainer
      throw e
    }
  } else {
    echo "Using VM agent"
    body()
  }
}
