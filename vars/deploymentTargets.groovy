#!groovy

/**
 * Temporary
 *
 * @param subscription
 * @param environment
 * @return
 */
def call(String subscription, String environment) {
  def deploymentTargets = [
    sandbox: [
      sandbox: ["v2"],
      saat: [],
      sprod: []
    ],
    nonprod: [
      aat: [],
      demo: [],
      preview: []
    ],
    prod: [
      prod: []
    ]]
  return deploymentTargets[subscription][environment]
}
