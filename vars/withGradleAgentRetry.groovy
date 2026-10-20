import uk.gov.hmcts.pipeline.AgentSelector

def call(String product, String environment, Closure body) {
  String agentLabel = AgentSelector.labelForEnvironment(environment, env, product) ?: env.BUILD_AGENT_TYPE
  String nodeSelector = agentLabel ? "${agentLabel} && !nightly" : '!nightly'

  retry(conditions: [agent()], count: 2) {
    node(nodeSelector) {
      deleteDir()
      checkout scm
      body()
    }
  }
}