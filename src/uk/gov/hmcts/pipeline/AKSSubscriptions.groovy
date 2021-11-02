package uk.gov.hmcts.pipeline

import uk.gov.hmcts.contino.Environment

class AKSSubscriptions {
  final AKSSubscription preview
  final AKSSubscription aat
  final AKSSubscription ithc
  final AKSSubscription perftest
  final AKSSubscription prod
  final AKSSubscription demo
  final AKSSubscription sandbox

  List<AKSSubscription> subscriptionList = new ArrayList<>();

  AKSSubscriptions(Object steps) {
    Objects.requireNonNull(steps)

    def environment = new Environment(steps.env)

    def previewName = steps.env.AKS_PREVIEW_SUBSCRIPTION_NAME ?: 'DCD-CNP-DEV'
    def previewId = steps.env.AKS_PREVIEW_SUBSCRIPTION_ID ?: '8b6ea922-0862-443e-af15-6056e1c9b9a4'
    preview = new AKSSubscription(steps, previewName, 'infra-vault-nonprod',  environment.previewName.toString(), previewId)
    subscriptionList.add(preview)

    def aatName = steps.env.AKS_AAT_SUBSCRIPTION_NAME ?: 'DCD-CFTAPPS-STG'
    def aatId = steps.env.AKS_AAT_SUBSCRIPTION_ID ?: '96c274ce-846d-4e48-89a7-d528432298a7'
    def aatKvName =  steps.env.AAT_AKS_KEY_VAULT ?: 'cftapps-stg'
    aat = new AKSSubscription(steps, aatName, aatKvName, environment.nonProdName.toString(), aatId)
    subscriptionList.add(aat)

    def perftestName = steps.env.AKS_PERFTEST_SUBSCRIPTION_NAME ?: 'DCD-CFTAPPS-TEST'
    def perftestId = steps.env.AKS_PERFTEST_SUBSCRIPTION_ID ?: '8a07fdcd-6abd-48b3-ad88-ff737a4b9e3c'
    perftest = new AKSSubscription(steps, perftestName, 'cftapps-test', environment.perftestName.toString(), perftestId)
    subscriptionList.add(perftest)

    def ithcName = steps.env.AKS_ITHC_SUBSCRIPTION_NAME ?: 'DCD-CFTAPPS-ITHC'
    def ithcId = steps.env.AKS_ITHC_SUBSCRIPTION_ID ?: '62864d44-5da9-4ae9-89e7-0cf33942fa09'
    ithc = new AKSSubscription(steps, ithcName, 'cftapps-ithc', environment.ithcName.toString(), ithcId)
    subscriptionList.add(ithc)

    def prodName = steps.env.AKS_PROD_SUBSCRIPTION_NAME ?: 'DCD-CFTAPPS-PROD'
    def prodKvName = steps.env.PROD_AKS_KEY_VAULT ?: 'cft-apps-prod'
    def prodId = steps.env.AKS_PROD_SUBSCRIPTION_ID ?: '8cbc6f36-7c56-4963-9d36-739db5d00b27'
    prod = new AKSSubscription(steps, prodName, prodKvName, environment.prodName.toString(), prodId)
    subscriptionList.add(prod)

    def demoName = steps.env.AKS_DEMO_SUBSCRIPTION_NAME ?: 'DCD-CFTAPPS-DEMO'
    def demoId = steps.env.AKS_DEMO_SUBSCRIPTION_ID ?: 'd025fece-ce99-4df2-b7a9-b649d3ff2060'
    demo = new AKSSubscription(steps, demoName, 'cftapps-demo', environment.demoName.toString(), demoId)
    subscriptionList.add(demo)

    def sandboxName = steps.env.AKS_SANDBOX_SUBSCRIPTION_NAME ?: 'DCD-CFTAPPS-SBOX'
    def sandboxId = steps.env.AKS_SANDBOX_SUBSCRIPTION_ID ?: 'b72ab7b7-723f-4b18-b6f6-03b0f2c6a1bb'
    sandbox = new AKSSubscription(steps, sandboxName, '', environment.sandbox.toString(), sandboxId)


  }

  AKSSubscription getAKSSubscriptionByEnvName(String environmentName) {

    return subscriptionList.find { it.envName == environmentName }

  }
}
