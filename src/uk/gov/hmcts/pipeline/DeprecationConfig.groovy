package uk.gov.hmcts.pipeline

class DeprecationConfig {

  def steps
  static def deprecationConfigInternal

  DeprecationConfig(steps){
    this.steps = steps
  }

  def getDeprecationConfig() {
    if (deprecationConfigInternal == null) {
      def response = steps.httpRequest(
        consoleLogResponseBody: true,
        timeout: 10,
        url: "https://raw.githubusercontent.com/hmcts/cnp-jenkins-config/master/deprecation-config.yml",
        validResponseCodes: '200'
      )
      deprecationConfigInternal = steps.readYaml(text: response.content)
    }
    return deprecationConfigInternal
  }

}
