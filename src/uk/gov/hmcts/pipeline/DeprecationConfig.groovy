package uk.gov.hmcts.pipeline

class DeprecationConfig {

  def steps
  static def deprecationConfig

  DeprecationConfig(steps){
    this.steps = steps
  }

  def loadDeprecationConfig(){
    if (deprecationConfig ==null ){
      def response = steps.httpRequest(
        consoleLogResponseBody: true,
        timeout: 10,
        url: "https://raw.githubusercontent.com/hmcts/cnp-jenkins-config/master/deprecation-config.yml",
        validResponseCodes: '200'
      )
      deprecationConfig = steps.readYaml(text: response.content)
    }
    return deprecationConfig
  }

}
