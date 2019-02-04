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
      aat: ["-v2"],
      demo: [],
      preview: []
    ],
    prod: [
      prod: ["-v2"]
    ]]
  return deploymentTargets[subscription][environment]
}
