def call(String s2sServiceName, String environment, String product) {

  camunda_url = "http://camunda-api-${environment}.service.core-compute-${environment}.internal"
  s2s_url = "http://rpe-service-auth-provider-${environment}.service.core-compute-${environment}.internal"

  stageWithAgent('Camunda - Publish BPMN and DMN', product) {

    log.info 'Publish to Camunda'

    functions = libraryResource 'uk/gov/hmcts/pipeline/camunda/publish-camunda-processes.sh'
    writeFile file: 'publish-camunda-processes.sh', text: functions
    sh """
    chmod +x publish-camunda-processes.sh
    ./publish-camunda-processes.sh $WORKSPACE $s2s_url $s2sServiceName $camunda_url
    """

    sh 'rm publish-camunda-processes.sh'
  }
}
