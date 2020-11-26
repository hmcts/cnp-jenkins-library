def call(String s2sServiceName, String environment, String product) {

  def camundaUrl = "http://camunda-api-${environment}.service.core-compute-${environment}.internal"
  def s2sUrl = "http://rpe-service-auth-provider-${environment}.service.core-compute-${environment}.internal"

  stageWithAgent('Camunda - Publish BPMN and DMN', product) {

    log.info 'Publish to Camunda'

    writeFile file: 'publish-camunda-processes.sh', text: libraryResource('uk/gov/hmcts/pipeline/camunda/publish-camunda-processes.sh')
    sh """
    chmod +x publish-camunda-processes.sh
    ./publish-camunda-processes.sh $WORKSPACE $s2sUrl $s2sServiceName $camundaUrl $product
    """

    sh 'rm publish-camunda-processes.sh'
  }
}
