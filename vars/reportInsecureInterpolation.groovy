import com.cloudbees.groovy.cps.NonCPS

def call() {
  def variables = interpolatedVariables()
  if (variables) {
    println "Warning: A secret was passed to a Pipeline step using Groovy String interpolation, which is insecure. Affected variable(s): ${variables}"
  }
}

@NonCPS
private List<String> interpolatedVariables() {
  def action = currentBuild.rawBuild.allActions.find {
    it.class.name == 'org.jenkinsci.plugins.workflow.cps.view.InterpolatedSecretsAction'
  }

  return action?.interpolatedVariables?.toList() ?: []
}