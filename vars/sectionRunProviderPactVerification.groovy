#!groovy
import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.contino.Builder
import uk.gov.hmcts.contino.PipelineCallbacksRunner

def call(params) {

  PipelineCallbacksRunner pcr = params.pipelineCallbacksRunner
  AppPipelineConfig config = params.appPipelineConfig
  Builder builder = params.builder

  def pactBrokerUrl = params.pactBrokerUrl

  if (config.pactBrokerEnabled) {
    stage("Pact Provider Verification") {
      def version = sh(returnStdout: true, script: 'git rev-parse --short HEAD')
      def isOnMaster = (env.BRANCH_NAME == 'master')

      env.PACT_BRANCH_NAME = isOnMaster ? env.BRANCH_NAME : env.CHANGE_BRANCH
      env.PACT_BROKER_URL = pactBrokerUrl

      if (config.pactProviderVerificationsEnabled) {
        pcr.callAround('pact-provider-verification') {
          builder.runProviderVerification(pactBrokerUrl, version, isOnMaster)
        }
      }
    }
  }
}
