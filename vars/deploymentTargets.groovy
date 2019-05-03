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
      sandbox: [],
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
  return deploymentTargets.get(subscription, [:])?.get(environment, [])
}
