import uk.gov.hmcts.pipeline.AgentSelector
import java.util.UUID

def call(String environment, String product, Closure body) {
  String agentLabel = AgentSelector.labelForEnvironment(environment, env)
  // Idempotency guard: nested calls should no-op once already running on the target agent.
  if (!agentLabel || agentLabel == env.BUILD_AGENT_TYPE) {
    body()
    return
  }

  String normalisedEnvironment = AgentSelector.normaliseEnvironment(environment)
  String stashName = "workspace-${normalisedEnvironment}-${env.BUILD_NUMBER ?: currentBuild?.number ?: 'local'}-${UUID.randomUUID()}"
  String originalDir = pwd()
  String relativeDir = ''
  if (env.WORKSPACE && originalDir?.startsWith(env.WORKSPACE)) {
    relativeDir = originalDir.substring(env.WORKSPACE.length()).replaceFirst('^/', '')
  }

  echo "Using ${agentLabel} agent for ${environment}"
  if (env.WORKSPACE) {
    dir(env.WORKSPACE) {
      stash name: stashName, includes: '**/*'
    }
  } else {
    stash name: stashName, includes: '**/*'
  }

  node(agentLabel) {
    if (env.WORKSPACE) {
      dir(env.WORKSPACE) {
        deleteDir()
        unstash stashName
      }
    } else {
      deleteDir()
      unstash stashName
    }
    withEnv([
      "BUILD_AGENT_TYPE=${agentLabel}",
      'BUILD_AGENT_CONTAINER=',
      'IS_DOCKER_BUILD_AGENT=false'
    ]) {
      dockerAgentSetup()
      if (!(env.PATH ?: '').split(':').contains('/usr/local/bin')) {
        env.PATH = "$env.PATH:/usr/local/bin"
      }
      if (env.ENVIRONMENT_AGENT_DEBUG == "true") {
        debugEnvironmentManagedIdentity(normalisedEnvironment, agentLabel)
      }
      try {
        if (relativeDir) {
          dir(relativeDir) {
            body()
          }
        } else {
          body()
        }
      } finally {
        if (env.KEEP_DIR_FOR_DEBUGGING != "true") {
          if (env.WORKSPACE) {
            dir(env.WORKSPACE) {
              deleteDir()
            }
          } else {
            deleteDir()
          }
        }
      }
    }
  }
}
