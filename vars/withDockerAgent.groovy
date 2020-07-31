import uk.gov.hmcts.pipeline.TeamConfig

/**
 * Run a closure inside a specific container of a kubernetes agent pod
 */
def call(String agentType, Closure body) {
  String agentContainer = new TeamConfig(this).getBuildAgentContainer(agentType)
  if (agentContainer != null && agentContainer != "") {
    try {
      container(agentContainer) {
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
