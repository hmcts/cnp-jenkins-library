package uk.gov.hmcts.pipeline

class AKSSubscriptions {
  final AKSSubscription preview
  final AKSSubscription aat
  final AKSSubscription ithc
  final AKSSubscription perftest
  final AKSSubscription prod
  final AKSSubscription demo
  final AKSSubscription ethosldata

  AKSSubscriptions(Object steps) {
    Objects.requireNonNull(steps)

    def previewName = steps.env.AKS_PREVIEW_SUBSCRIPTION_NAME ?: 'DCD-CNP-DEV'
    preview = new AKSSubscription(steps, previewName, 'infra-vault-nonprod',  'preview', false)

    def aatName = steps.env.AKS_AAT_SUBSCRIPTION_NAME ?: 'DCD-CFTAPPS-STG'
    def aatKvName =  steps.env.AAT_AKS_KEY_VAULT ?: 'cftapps-stg'
    aat = new AKSSubscription(steps, aatName, aatKvName, 'aat', true)

    def perftestName = steps.env.AKS_PERFTEST_SUBSCRIPTION_NAME ?: 'DCD-CFTAPPS-TEST'
    perftest = new AKSSubscription(steps, perftestName, 'cftapps-test', 'perftest', true)

    def ithcName = steps.env.AKS_ITHC_SUBSCRIPTION_NAME ?: 'DCD-CFTAPPS-ITHC'
    ithc = new AKSSubscription(steps, ithcName, 'cftapps-ithc', 'ithc', true)

    def prodName = steps.env.AKS_PROD_SUBSCRIPTION_NAME ?: 'DCD-CFTAPPS-PROD'
    def prodKvName = steps.env.PROD_AKS_KEY_VAULT ?: 'cft-apps-prod'
    prod = new AKSSubscription(steps, prodName, prodKvName, 'prod', true)

    def demoName = steps.env.AKS_DEMO_SUBSCRIPTION_NAME ?: 'DCD-CFTAPPS-DEMO'
    demo = new AKSSubscription(steps, demoName, 'cftapps-demo', 'demo', true)

    def ethosLdataName = steps.env.AKS_ETHOS_LDATA_SUBSCRIPTION_NAME ?: 'DCD-ETHOS-MIGRATION-LDATA'
    demo = new AKSSubscription(steps, ethosLdataName, 'ethos-ldata', 'ethosldata', true)

  }
}
