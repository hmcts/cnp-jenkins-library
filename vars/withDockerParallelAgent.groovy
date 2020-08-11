/**
 * Run a closure inside a specific container of a kubernetes agent pod (or skip container altogether)
 */
def call(Map<String, Closure> bodies, boolean failFast) {
  String agentContainer = env.BUILD_AGENT_CONTAINER
  int agentContainerInstances = env.BUILD_AGENT_CONTAINER_PAR == "" ? 1 : env.BUILD_AGENT_CONTAINER_PAR as int
  if (agentContainer != null && agentContainer != "") {
    echo "Docker agent containers: ${agentContainer} #${agentContainerInstances}"
    def stageDefs = [:]
    int i = 0
    for (body in bodies) {
      int inst = i % agentContainerInstances
      String agentContainerInstance = inst == 0 ? agentContainer : "${agentContainer}-${inst}"
      stageDefs[body.key] = {
        try {
          container(agentContainerInstance) {
            echo "Using agent container: ${agentContainerInstance}"
            dockerAgentSetup()
            body.value()
          }
        } catch (Exception e ) {
          containerLog agentContainer
          throw e
        }
      }
    }
    stageDefs.failFast = failFast
    parallel stageDefs
  } else {
    bodies.failFast = failFast
    parallel bodies
  }
}
