import uk.gov.hmcts.pipeline.AgentSelector

def call(String product, String environment, Closure body) {
  String agentLabel = AgentSelector.labelForEnvironment(environment, env, product) ?: env.BUILD_AGENT_TYPE
  String nodeSelector = agentLabel ? "${agentLabel} && !nightly" : '!nightly'
  boolean firstAttempt = true

  retry(conditions: [agent()], count: 2) {
    if (firstAttempt) {
      firstAttempt = false
      body()
    } else {
      node(nodeSelector) {
        deleteDir()
        checkout scm
        body()
      }
    }
  }
}