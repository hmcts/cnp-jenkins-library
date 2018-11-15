import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

def call(Map params, Closure body) {
  try {
    timeout(time: params.time, unit: params.unit) {
      body.call()
    }
  } catch (FlowInterruptedException exc) {
    echo("TIMEOUT : ${params.action}")
    throw exc
  }
}
