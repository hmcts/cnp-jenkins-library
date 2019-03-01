import uk.gov.hmcts.contino.Environment
import uk.gov.hmcts.contino.Subscription

def call() {

  def branch = env.BRANCH_NAME

  def environment = new Environment(env)
  def subscription = new Subscription(env)

  // map of branch name to environment
  def autoDeployEnvironments = [
    demo: [
      environmentName: environment.demoName,
      subscriptionName: subscription.nonProdName
    ],
    hmctsdemo: [
      environmentName: environment.hmctsDemoName,
      subscriptionName: subscription.hmctsDemoName
    ],
    perftest: [
      environmentName: environment.perftestName,
      subscriptionName: subscription.qaName
    ]
  ]
  return autoDeployEnvironments[branch]
}
