/**
 * Run a closure inside a specific container of a kubernetes agent pod (or skip container altogether)
 */
def call(Map<String, Closure> preStages, Map<String, Closure> stages, boolean failFast) {
  String agentContainer = env.BUILD_AGENT_CONTAINER
  int agentContainerInstances = env.BUILD_AGENT_CONTAINER_PAR == "" ? 1 : env.BUILD_AGENT_CONTAINER_PAR as int
  // run pre-stages
  preStages.each{ entry ->
    entry.value()
  }
  // run parallel stages
  if (agentContainer != null && agentContainer != "") {
    echo "Docker agent containers: ${agentContainer} #${agentContainerInstances}"
    def stageDefs = [:]
    int i = 0
    stages.eachWithIndex { entry, idx ->
      int inst = i % agentContainerInstances
      String agentContainerInstance = inst == 0 ? agentContainer : "${agentContainer}-${inst}"
      stageDefs[entry.key] = {
        try {
          container(agentContainerInstance) {
            echo "Using agent container ${agentContainerInstance} to process ${entry.key}"
            dockerAgentSetup()
            entry.value()
          }
        } catch (Exception e ) {
          containerLog agentContainer
          throw e
        }
      }
      i++
    }
    stageDefs.failFast = failFast
    parallel stageDefs
  } else {
    stages.failFast = failFast
    parallel stages
  }
}
