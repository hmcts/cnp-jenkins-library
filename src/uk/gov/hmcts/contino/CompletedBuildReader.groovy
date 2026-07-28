package uk.gov.hmcts.contino

import com.cloudbees.groovy.cps.NonCPS
import hudson.FilePath
import jenkins.model.Jenkins

/**
 * Reads completed builds directly from the Jenkins controller.
 *
 * This class deliberately has no state so it remains safe to retain across
 * Pipeline suspensions while the archive worker waits for a source build.
 */
class CompletedBuildReader implements Serializable {

  private static final long serialVersionUID = 1L

  @NonCPS
  Map snapshot(String jobName, int buildNumber) {
    def build = requiredBuild(jobName, buildNumber)

    [
      building: build.isBuilding(),
      result: build.getResult()?.toString(),
      number: build.getNumber(),
      url: build.getUrl(),
      displayName: build.getDisplayName(),
      fullDisplayName: build.getFullDisplayName(),
      timestamp: build.getTimeInMillis(),
      duration: build.getDuration(),
      estimatedDuration: build.getEstimatedDuration(),
      workflow: workflowMetadata(build),
      tests: testMetadata(build)
    ]
  }

  @NonCPS
  void copyOutputs(String jobName, int buildNumber, FilePath archiveDirectory) {
    def build = requiredBuild(jobName, buildNumber)
    if (build.isBuilding()) {
      throw new IllegalStateException("Refusing to copy outputs from running build ${jobName} #${buildNumber}")
    }

    archiveDirectory.mkdirs()
    copyConsole(build, archiveDirectory.child('console.txt'))
    copyArtifacts(build, archiveDirectory.child('artifacts.zip'))
  }

  @NonCPS
  private static def requiredBuild(String jobName, int buildNumber) {
    def job = Jenkins.get().getItemByFullName(jobName)
    if (job == null) {
      throw new IllegalArgumentException("Unable to find Jenkins job: ${jobName}")
    }

    def build = job.getBuildByNumber(buildNumber)
    if (build == null) {
      throw new IllegalArgumentException("Unable to find Jenkins build: ${jobName} #${buildNumber}")
    }

    build
  }

  @NonCPS
  private static void copyConsole(def build, FilePath target) {
    OutputStream output = target.write()
    try {
      build.writeWholeLogTo(output)
    } finally {
      output.close()
    }
  }

  @NonCPS
  private static void copyArtifacts(def build, FilePath target) {
    if (!build.hasArtifact()) {
      return
    }

    OutputStream output = target.write()
    try {
      build.getArtifactManager().root().zip(output, '**', null, true, '')
    } finally {
      output.close()
    }
  }

  @NonCPS
  static Map testMetadata(def build) {
    def action = build.getAllActions().find { candidate ->
      candidate != null &&
        hasNoArgMethod(candidate, 'getFailCount') &&
        hasNoArgMethod(candidate, 'getSkipCount') &&
        hasNoArgMethod(candidate, 'getTotalCount')
    }
    if (action == null) {
      return null
    }

    int totalCount = action.getTotalCount() as int
    int failCount = action.getFailCount() as int
    int skipCount = action.getSkipCount() as int

    [
      totalCount: totalCount,
      failCount: failCount,
      skipCount: skipCount,
      passCount: totalCount - failCount - skipCount
    ]
  }

  private static boolean hasNoArgMethod(def target, String methodName) {
    target.getClass().getMethods().any { method ->
      method.name == methodName && method.parameterCount == 0
    }
  }

  @NonCPS
  private static Map workflowMetadata(def build) {
    if (!build.metaClass.respondsTo(build, 'getExecution')) {
      return null
    }

    def execution = build.getExecution()
    if (execution == null) {
      return null
    }

    try {
      Class walkerType = build.class.classLoader.loadClass(
        'org.jenkinsci.plugins.workflow.graph.FlowGraphWalker'
      )
      def walker = walkerType.newInstance(execution)
      List nodes = walker.iterator().collect { it }
      List stages = nodes.findResults { node ->
        def stageAction = node.getAllActions().find { action ->
          action.class.name == 'org.jenkinsci.plugins.workflow.actions.StageAction'
        }
        if (stageAction == null) {
          return null
        }

        boolean failed = nodes.any { candidate ->
          hasError(candidate) &&
            (candidate == node || candidate.getEnclosingBlocks().contains(node))
        }
        [
          name: stageAction.getStageName(),
          status: failed ? 'FAILED' : 'SUCCESS'
        ]
      }.reverse()

      stages ? [stages: stages] : null
    } catch (Exception ignored) {
      null
    }
  }

  @NonCPS
  private static boolean hasError(def node) {
    node.getAllActions().any { action ->
      action.class.name == 'org.jenkinsci.plugins.workflow.actions.ErrorAction'
    }
  }
}
