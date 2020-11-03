import uk.gov.hmcts.contino.*

def call(String s2sServiceName, String component, String environment, String product) {

  stageWithAgent("Camunda - Publish BPMN and DMN", product) {

    if ( new ProjectBranch(env.BRANCH_NAME).isPR() && env.CHANGE_TITLE.startsWith('[PREVIEW]') ) {
      camunda = "camunda-$product-$component-$projectBranch.imageTag()"
      s2s_url = "http://rpe-service-auth-provider-aat.service.core-compute-aat.internal"
    } else {
      camunda = "camunda-api-$environment"
      s2s_url = "http://rpe-service-auth-provider-${environment}.service.core-compute-${environment}.internal"
    }

    camunda_url = "http://${camunda}.service.core-compute-${environment}.internal"

    log.info "Publish to Camunda"

    def functions = libraryResource 'uk/gov/hmcts/pipeline/camunda/publish-camunda-processes.sh'
    writeFile file: 'publish-camunda-processes.sh', text: functions
    sh """
    chmod +x publish-camunda-processes.sh
    ./publish-camunda-processes.sh $WORKSPACE $s2s_url $s2sServiceName $camunda_url
    """

    sh 'rm publish-camunda-processes.sh'
  }
}

