def call(List secrets, Closure body) {
  recordSecretUsage(secrets)
  steps.withAzureKeyvault(secrets, body)
}

def call(Map arguments, Closure body) {
  recordSecretUsage(arguments.azureKeyVaultSecrets ?: [])
  steps.withAzureKeyvault(arguments, body)
}

private void recordSecretUsage(Collection secrets) {
  secrets.collect { secret ->
    secret instanceof Map ? secret.name : secret.properties?.name
  }.findAll { it }.each { secretName ->
    println "Recording usage and performing telemetry or whatever for Azure Key Vault secret: ${secretName}"
  }
}