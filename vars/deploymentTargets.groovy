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
      sandbox: ["-v2"],
      saat: ["-v2"],
      sprod: ["-v2"]
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
