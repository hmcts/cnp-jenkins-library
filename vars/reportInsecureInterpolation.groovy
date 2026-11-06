import com.cloudbees.groovy.cps.NonCPS

def call() {
  def warnings = interpolatedWarnings()
  if (!warnings) {
    return
  }

  println "Insecure interpolation detected: pipeline=${env.JOB_NAME ?: 'unknown'}, branch=${env.BRANCH_NAME ?: 'unknown'}, run=${env.BUILD_NUMBER ?: 'unknown'}"
  warnings.each { stepName, variables ->
    println "${stepName}: ${variables}"
  }
}

@NonCPS
private Map<String, List<String>> interpolatedWarnings() {
  def action = currentBuild.rawBuild?.allActions?.find { currentAction ->
    currentAction != null && currentAction.getClass().getName() == 'org.jenkinsci.plugins.workflow.cps.view.InterpolatedSecretsAction'
  }

  def warningsByStep = [:].withDefault { [] }
  action?.getWarnings()?.each { warning ->
    def stepName = warning?.getStepName()
    if (stepName) {
      warningsByStep[stepName].addAll(warning.getInterpolatedVariables() ?: [])
    }
  }

  return warningsByStep.collectEntries { stepName, variables ->
    [(stepName): variables.unique()]
  }
}