import uk.gov.hmcts.contino.*

def call(String s2sServiceName, String camundaUrl, String s2sUrl, String product) {

  stageWithAgent("Camunda - Publish BPMN and DMN", product) {

    log.info "Publish to Camunda"

    def functions = libraryResource 'uk/gov/hmcts/pipeline/camunda/publish-camunda-processes.sh'
    writeFile file: 'publish-camunda-processes.sh', text: functions
    sh """
    chmod +x publish-camunda-processes.sh
    ./publish-camunda-processes.sh $WORKSPACE $s2sUrl $s2sServiceName $camundaUrl
    """

    sh 'rm publish-camunda-processes.sh'
  }
}

