import uk.gov.hmcts.contino.Environment
import uk.gov.hmcts.contino.Subscription
import uk.gov.hmcts.pipeline.AKSSubscriptions

def call() {

  def branch = env.BRANCH_NAME

  def environment = new Environment(env)
  def subscription = new Subscription(env)
  def aksSubscriptions = new AKSSubscriptions(this)

  // map of branch name to environment
  def autoDeployEnvironments = [
    preview: [
      environmentName: environment.previewName,
      subscriptionName: subscription.nonProdName,
      aksSubscription: aksSubscriptions.preview,
      aksInfraRgName: null
    ],
    demo: [
      environmentName: environment.demoName,
      subscriptionName: subscription.demoName,
      aksSubscription: aksSubscriptions.demoName,
      aksInfraRgName: null
    ],
    hmctsdemo: [ // TODO delete
      environmentName: environment.hmctsDemoName,
      subscriptionName: subscription.hmctsDemoName
    ],
    perftest: [
      environmentName: environment.perftestName,
      subscriptionName: subscription.qaName,
      aksSubscription: aksSubscriptions.perftest
    ],
    ithc: [
      environmentName: environment.ithcName,
      subscriptionName: subscription.qaName,
      aksSubscription: aksSubscriptions.ithc
    ],
    ethosldata: [
      environmentName: environment.ethosLdataName,
      subscriptionName: subscription.ethosLdataName,
      aksSubscription: null,
      aksInfraRgName: null
    ]
  ]
  return autoDeployEnvironments[branch]
}
