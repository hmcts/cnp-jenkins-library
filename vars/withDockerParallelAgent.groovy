/**
 * Run a closure inside a specific container of a kubernetes agent pod (or skip container altogether)
 */
def call(Map<String, Tuple2<Closure, Closure>> stages, boolean failFast) {
  String agentContainer = env.BUILD_AGENT_CONTAINER
  int agentContainerInstances = env.BUILD_AGENT_CONTAINER_PAR == "" ? 1 : env.BUILD_AGENT_CONTAINER_PAR as int
  def stageDefs = [:]
  if (agentContainer != null && agentContainer != "") {
    echo "Docker agent containers: ${agentContainer} #${agentContainerInstances}"
    int i = 0
    stages.eachWithIndex { entry, idx ->
      int inst = i % agentContainerInstances
      String agentContainerInstance = inst == 0 ? agentContainer : "${agentContainer}-${inst}"
      // run pre-stages (on the same container as related parallel stage)
      if (entry.value[0] != null) {
        container(agentContainerInstance) {
          echo "Using agent container ${agentContainerInstance} to process pre-stage ${entry.key}"
          entry.value[0]()
        }
      }
      // prepare parallel stages
      stageDefs[entry.key] = {
        try {
          container(agentContainerInstance) {
            echo "Using agent container ${agentContainerInstance} to process ${entry.key}"
            dockerAgentSetup()
            entry.value[1]()
          }
        } catch (Exception e ) {
          containerLog agentContainer
          throw e
        }
      }
      i++
    }
  } else {
    stages.eachWithIndex { entry, idx ->
      // run pre-stages
      if (entry.value[0] != null) {
        entry.value[0]()
      }
      // prepare parallel stages
      stageDefs[entry.key] = {
        entry.value[1]()
      }
    }
  }
  // run parallel stages
  stageDefs.failFast = failFast
  parallel stageDefs
}
