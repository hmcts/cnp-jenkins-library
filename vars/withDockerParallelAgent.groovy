/**
 * Run a closure inside a specific container of a kubernetes agent pod (or skip container altogether)
 */
def call(Map<String, Closure> parallelStages) {
  String agentContainer = env.BUILD_AGENT_CONTAINER
  int agentContainerInstances = env.BUILD_AGENT_CONTAINER_PAR == "" ? 1 : env.BUILD_AGENT_CONTAINER_PAR as int
  if (agentContainer != null && agentContainer != "") {
    echo "Docker agent containers: ${agentContainer} #${agentContainerInstances}"
    def stageDefs = [:]
    int i = 0
    for (stg in parallelStages) {
      int inst = i % agentContainerInstances
      String agentContainerInstance = inst == 0 ? agentContainer : "${agentContainer}-${inst}"
      stageDefs[stg.key] = {
        try {
          container(agentContainerInstance) {
            echo "Using agent container: ${agentContainerInstance}"
            dockerAgentSetup()
            stg.value.call()
          }
        } catch (Exception e ) {
          containerLog agentContainer
          throw e
        }
      }
      i++
    }
    parallel stageDefs
  } else {
    parallel parallelStages
  }
}
