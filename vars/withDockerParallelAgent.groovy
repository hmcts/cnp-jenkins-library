/**
 * Run a closure inside a specific container of a kubernetes agent pod (or skip container altogether)
 */
def call(Map<String, Closure> bodies, boolean failFast) {
  String agentContainer = env.BUILD_AGENT_CONTAINER
  if (agentContainer != null && agentContainer != "") {
    echo "Using agent container: ${agentContainer}"
    def stageDefs = [:]
    for (body in bodies) {
      stageDefs[body.key] = {
        try {
          container(agentContainer) {
            dockerAgentSetup()
            body.value
          }
        } catch (Exception e ) {
          containerLog agentContainer
          throw e
        }
      }
    }
    parallel stageDefs, failFast: failFast
  } else {
    parallel bodies, failFast: failFast
  }
}
