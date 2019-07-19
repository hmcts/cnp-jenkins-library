import uk.gov.hmcts.contino.Environment
import uk.gov.hmcts.contino.Subscription
import uk.gov.hmcts.contino.AKSSubscription

def call() {

  def branch = env.BRANCH_NAME

  def environment = new Environment(env)
  def subscription = new Subscription(env)
  def aksSubscription = new AKSSubscription(env)

  // map of branch name to environment
  def autoDeployEnvironments = [
    preview: [
      environmentName: environment.previewName,
      subscriptionName: subscription.nonProdName,
      aksSubscriptionName: aksSubscription.previewName,
      aksInfraRgName: null
    ],
    demo: [
      environmentName: environment.demoName,
      subscriptionName: subscription.nonProdName,
      aksSubscriptionName: null,
      aksInfraRgName: null
    ],
    hmctsdemo: [ // TODO delete
      environmentName: environment.hmctsDemoName,
      subscriptionName: subscription.hmctsDemoName
    ],
    perftest: [
      environmentName: environment.perftestName,
      subscriptionName: subscription.qaName,
      aksSubscriptionName: aksSubscription.perftestName,
      aksInfraRgName: aksSubscription.perftestInfraRgName
    ],
    ithc: [
      environmentName: environment.ithcName,
      subscriptionName: subscription.qaName,
      aksSubscriptionName: aksSubscription.ithcName,
      aksInfraRgName: aksSubscription.ithcInfraRgName
    ],
    ethosldata: [
      environmentName: environment.ethosLdataName,
      subscriptionName: subscription.ethosLdataName,
      aksSubscriptionName: null,
      aksInfraRgName: null
    ]
  ]
  return autoDeployEnvironments[branch]
}
