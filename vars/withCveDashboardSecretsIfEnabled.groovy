def call(config, String product, String environment, Closure body) {
  if (!shouldPublishCveDashboardSnapshot(config)) {
    body.call()
    return
  }

  def dashboardEnvironment = environment?.trim()
  def secrets = [
    [secretType: 'Secret', name: 'cve-dashboard-cve-intake-api-key', version: '', envVariable: 'CVE_DASHBOARD_API_KEY']
  ]

  withEnv([
    "CVE_DASHBOARD_URL=https://cve-dashboard.${dashboardEnvironment}.platform.hmcts.net",
    "CVE_DASHBOARD_PUBLISH_BRANCHES=${normaliseCveDashboardBranches(config.cveDashboardIngestionBranches).join(',')}"
  ]) {
    withAzureKeyvault(azureKeyVaultSecrets: secrets, keyVaultURLOverride: 'https://ccd-aat.vault.azure.net/') {
      body.call()
    }
  }
}

def shouldPublishCveDashboardSnapshot(config) {
  if (!config.cveDashboardIngestion) {
    return false
  }

  def branchName = env.BRANCH_NAME?.trim()
  branchName && normaliseCveDashboardBranches(config.cveDashboardIngestionBranches).contains(branchName)
}

private List<String> normaliseCveDashboardBranches(branches) {
  def normalised = (branches instanceof Collection ? branches : [])
    .collect { it?.toString()?.trim() }
    .findAll { it }
    .unique()

  normalised ?: ['master']
}
