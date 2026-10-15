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
  if (AgentSelector.currentAgentMatchesLabel(env, agentLabel)) {
    withEnvironmentContext(agentLabel, environment, body)
    return
  }

  String stashName = "workspace-${normalisedEnvironment}-${env.BUILD_NUMBER ?: currentBuild?.number ?: 'local'}-${UUID.randomUUID()}"
  String updatedStashName = "${stashName}-updated"
  String stashIncludes = env.ENVIRONMENT_AGENT_STASH_INCLUDES ?: '**/*'
  boolean stashBack = env.ENVIRONMENT_AGENT_STASH_BACK != 'false'
  String stashExcludes = [
    '.yarn_dependencies_installed',
    '**/.yarn_dependencies_installed',
    '.terraform/**',
    '**/.terraform/**',
    'node_modules/**',
    '**/node_modules/**',
    '.yarn/install-state.gz',
    '**/.yarn/install-state.gz',
    '.yarn/**',
    '**/.yarn/**',
    '.gradle/**',
    '**/.gradle/**',
    'vendor/bundle/**',
    '**/vendor/bundle/**',
    '.bundle/cache/**',
    '**/.bundle/cache/**',
    '.venv/**',
    '**/.venv/**',
    '**build/**',
    '**/build/**',
    '.gradle-user-home/**',
    '**/.gradle-user-home/**',
    '**/src/**'
  ].join(',')
  boolean updatedWorkspaceStashed = false
  String originalDir = pwd()
  String relativeDir = ''
  // Keep the caller's workspace-relative directory after switching nodes.
  if (env.WORKSPACE && originalDir?.startsWith(env.WORKSPACE)) {
    relativeDir = originalDir.substring(env.WORKSPACE.length()).replaceFirst('^/', '')
  }

  echo "Using ${agentLabel} agent for ${environment}"
  inOriginalWorkspace {
    logStashSummary(stashName, stashExcludes, stashIncludes)
    stash name: stashName, includes: stashIncludes, excludes: stashExcludes
  }

  node(agentLabel) {
    inOriginalWorkspace {
      deleteDir()
      unstash stashName
      restoreGitMetadataIfRequired()
    }
    withEnvironmentContext(agentLabel, environment) {
      if (!(env.PATH ?: '').split(':').contains('/usr/local/bin')) {
        env.PATH = "$env.PATH:/usr/local/bin"
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
        if (bodySucceeded && stashBack) {
          inOriginalWorkspace {
            logStashSummary(updatedStashName, stashExcludes, stashIncludes)
            stash name: updatedStashName, includes: stashIncludes, excludes: stashExcludes
          }
          updatedWorkspaceStashed = true
        }
        if (env.KEEP_DIR_FOR_DEBUGGING != "true") {
          inOriginalWorkspace {
            deleteDir()
          }
        }
      }
    }
  }

  // Preserve generated files from env-agent stages for later stages that restash
  // from the original workspace, while still avoiding expensive dependency dirs.
  if (updatedWorkspaceStashed) {
    inOriginalWorkspace {
      unstash updatedStashName
    }
  }
}

def logStashSummary(String stashName, String stashExcludes, String stashIncludes = '**/*') {
  if (env.STASH_DIAGNOSTICS != 'true') {
    return
  }

  def folderCounts = [:]
  int totalFiles = 0
  findFiles(glob: stashIncludes, excludes: stashExcludes).each { entry ->
    if (!entry.directory) {
      String folder = entry.path.contains('/') ? entry.path.substring(0, entry.path.indexOf('/')) + '/' : '(root files)'
      folderCounts[folder] = (folderCounts[folder] ?: 0) + 1
      totalFiles++
    }
  }

  echo "Stash summary for ${stashName}: ${totalFiles} files (recursive counts by top-level folder)"
  folderCounts.keySet().sort().each { folder ->
    echo "  ${folder}: ${folderCounts[folder]} files"
  }
}

def inOriginalWorkspace(Closure body) {
  if (env.WORKSPACE) {
    dir(env.WORKSPACE) {
      body()
    }
  } else {
    body()
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
