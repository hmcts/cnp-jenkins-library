import uk.gov.hmcts.contino.*

def call(String s2sServiceName, String component, String environment, String product) {

  stageWithAgent("Camunda - Publish BPMN and DMN") {

    if ( new ProjectBranch(env.BRANCH_NAME).isPR() && env.CHANGE_TITLE.startsWith('[PREVIEW]') ) {
      camunda = "camunda-$product-$component-$projectBranch.imageTag()"
    } else {
      camunda = "camunda-api-$environment"
    }

    camunda_url = "http://${camunda}.service.core-compute-${environment}.internal"

    log.info "Publish to Camunda"

    def functions = libraryResource 'uk/gov/hmcts/pipeline/camunda/publishBpmn.sh'
    writeFile file: 'publishBpmn.sh', text: functions
    result = sh "bash publishBpmn.sh $WORKSPACE $S2S_URL $s2sServiceName $camunda_url"
  }
}

