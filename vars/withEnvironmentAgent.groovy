import uk.gov.hmcts.pipeline.AgentSelector
import java.util.UUID

def call(String environment, String product, Closure body) {
  call(environment, product, null, body)
}

def call(String environment, String product, String agentLabelOverride, Closure body) {
  String agentLabel = agentLabelOverride ?: AgentSelector.labelForEnvironment(environment, env, product)
  // Idempotency guard: nested calls should no-op once already running on the target agent.
  if (!agentLabel) {
    body()
    return
  }

  String normalisedEnvironment = AgentSelector.normaliseEnvironment(environment)
  if (agentLabel == env.BUILD_AGENT_TYPE) {
    withEnvironmentContext(agentLabel, environment, body)
    return
  }

  String stashName = "workspace-${normalisedEnvironment}-${env.BUILD_NUMBER ?: currentBuild?.number ?: 'local'}-${UUID.randomUUID()}"
  String updatedStashName = "${stashName}-updated"
  String stashExcludes = [
    '.yarn_dependencies_installed',
    '**/.yarn_dependencies_installed',
    '.terraform/**',
    '**/.terraform/**',
    'node_modules/**',
    '**/node_modules/**',
    '.yarn/cache/**',
    '**/.yarn/cache/**',
    '.gradle/**',
    '**/.gradle/**'
  ].join(',')
  boolean updatedWorkspaceStashed = false
  String originalDir = pwd()
  String relativeDir = ''
  // Keep the caller's workspace-relative directory after switching nodes.
  if (env.WORKSPACE && originalDir?.startsWith(env.WORKSPACE)) {
    relativeDir = originalDir.substring(env.WORKSPACE.length()).replaceFirst('^/', '')
  }

  echo "Using ${agentLabel} agent for ${environment}"
  if (env.WORKSPACE) {
    dir(env.WORKSPACE) {
      stash name: stashName, includes: '**/*', excludes: stashExcludes
    }
  } else {
    stash name: stashName, includes: '**/*', excludes: stashExcludes
  }

  node(agentLabel) {
    if (env.WORKSPACE) {
      dir(env.WORKSPACE) {
        deleteDir()
        unstash stashName
        restoreGitMetadataIfRequired()
      }
    } else {
      deleteDir()
      unstash stashName
      restoreGitMetadataIfRequired()
    }
    withEnvironmentContext(agentLabel, environment) {
      if (!(env.PATH ?: '').split(':').contains('/usr/local/bin')) {
        env.PATH = "$env.PATH:/usr/local/bin"
      }
      if (env.ENVIRONMENT_AGENT_DEBUG == "true") {
        debugEnvironmentManagedIdentity(normalisedEnvironment, agentLabel)
      }
      boolean bodySucceeded = false
      try {
        if (relativeDir) {
          dir(relativeDir) {
            body()
          }
        } else {
          body()
        }
        bodySucceeded = true
      } finally {
        if (bodySucceeded) {
          if (env.WORKSPACE) {
            dir(env.WORKSPACE) {
              stash name: updatedStashName, includes: '**/*', excludes: stashExcludes
              updatedWorkspaceStashed = true
            }
          } else {
            stash name: updatedStashName, includes: '**/*', excludes: stashExcludes
            updatedWorkspaceStashed = true
          }
        }
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

  // Preserve generated files from env-agent stages for later stages that restash
  // from the original workspace, while still avoiding expensive dependency dirs.
  if (updatedWorkspaceStashed) {
    if (env.WORKSPACE) {
      dir(env.WORKSPACE) {
        unstash updatedStashName
      }
    } else {
      unstash updatedStashName
    }
  }
}

def withEnvironmentContext(String agentLabel, String environment, Closure body) {
  withEnv([
    "BUILD_AGENT_TYPE=${agentLabel}",
    "DEPLOYMENT_ENVIRONMENT=${environment}",
    'BUILD_AGENT_CONTAINER=',
    'IS_DOCKER_BUILD_AGENT=false'
  ]) {
    body()
  }
}

def restoreGitMetadataIfRequired() {
  if (fileExists('.git')) {
    return
  }

  String remoteUrl = env.ORIGINAL_REMOTE_URL ?: env.GIT_URL
  if (!remoteUrl) {
    return
  }

  // Jenkins stash excludes .git; restore only the remote metadata needed by tools
  // that call `git config remote.origin.url`, not the full commit history.
  sh(
    label: 'Restore git metadata',
    script: """
      #!/usr/bin/env bash
      set -e
      git init -q
      git remote add origin '${remoteUrl.replace("'", "'\\''")}'
    """.stripIndent()
  )
}
