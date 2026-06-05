import uk.gov.hmcts.contino.AppPipelineConfig
import uk.gov.hmcts.pipeline.AgentSelector

def call(config, String environment, Closure body) {
  call(config, environment, env.PRODUCT ?: env.RAW_PRODUCT_NAME ?: '', body)
}

def call(config, String environment, String product, Closure body) {
  Map<String, List<Map<String, Object>>> secrets = config.vaultSecrets
  Map<String, String> vaultOverrides = config.vaultEnvironmentOverrides

  if (secrets.isEmpty()) {
    body.call()
    return
  }

  executeClosure(secrets.entrySet().iterator(), environment, product, vaultOverrides) {
    body.call()
  }
}

def executeClosure(Iterator<Map.Entry<String,List<Map<String,Object>>>> secretIterator, String environment, String product, Map<String, String> vaultOverrides, Closure body) {
  //noinspection ChangeToOperator doesn't work in jenkins
  def entry = secretIterator.next()

  String theKeyVaultUrl = getKeyVaultUrl(entry, environment, vaultOverrides)

  if (AgentSelector.isRunningOnEnvironmentAgent(env, null, product)) {
    withEnvironmentManagedIdentitySecrets(entry.value, theKeyVaultUrl) {
      if (secretIterator.hasNext()) {
        return executeClosure(secretIterator, environment, product, vaultOverrides, body)
      } else {
        body.call()
      }
    }
    return
  }

  withAzureKeyvault(
    azureKeyVaultSecrets: entry.value,
    keyVaultURLOverride: theKeyVaultUrl,
    applicationIDOverride: env.AZURE_CLIENT_ID,
    applicationSecretOverride: env.AZURE_CLIENT_SECRET
  ) {
    if (secretIterator.hasNext()) {
      return executeClosure(secretIterator, environment, product, vaultOverrides, body)
    } else {
      body.call()
    }
  }
}

@SuppressWarnings("GrMethodMayBeStatic") // no idea how a static method would work inside a jenkins step...
private String getKeyVaultUrl(Map.Entry<String, List<Map<String, Object>>> entry, String environment, Map<String, String> vaultOverrides) {
  def vaultEnv = vaultOverrides.get(environment, environment)
  return "https://${entry.key.replace('${env}', vaultEnv)}.vault.azure.net/"
}

private void withEnvironmentManagedIdentitySecrets(List<Map<String, Object>> secrets, String keyVaultUrl, Closure body) {
  String vaultName = vaultNameFromUrl(keyVaultUrl)
  String azureConfigName = AgentSelector.normaliseEnvironment(env.DEPLOYMENT_ENVIRONMENT)
  String azureConfigDir = "/opt/jenkins/.azure-${azureConfigName}"
  boolean retryWithLegacyCredentials = shouldRetryWithLegacySecretReader(azureConfigName, vaultName)

  loginWithManagedIdentity(azureConfigDir)

  List<String> variables
  try {
    variables = secrets.collect { Map<String, Object> secret ->
      String secretName = secret.name.toString()
      String envVariable = secret.envVariable.toString()
      String secretValue = readKeyVaultSecret(vaultName, secretName, azureConfigDir).trim()

      "${envVariable}=${secretValue}"
    }
  } catch (Exception primaryError) {
    if (!retryWithLegacyCredentials) {
      throw primaryError
    }

    try {
      echo "Preview Jenkins MI cannot read ${vaultName}; retrying with legacy Key Vault credentials"
      variables = readLegacyKeyVaultSecrets(secrets, keyVaultUrl)
    } catch (Exception fallbackError) {
      throw new RuntimeException("Preview Jenkins MI could not read ${vaultName}; legacy Key Vault credentials retry also failed: ${fallbackError.message}", fallbackError)
    }
  }

  // Team secrets are expected to be single-line values. If a future consumer
  // needs PEM/RSA-style multi-line secrets, pass a file path instead.
  withEnv(variables) {
    body.call()
  }
}

private String readKeyVaultSecret(String vaultName, String secretName, String azureConfigDir) {
  sh(
    label: "az keyvault secret show ${vaultName}/${secretName}",
    script: """
      set +x
      error_file=\$(mktemp)
      secret_value=\$(env AZURE_CONFIG_DIR='${shellQuote(azureConfigDir)}' az keyvault secret show --vault-name '${shellQuote(vaultName)}' --name '${shellQuote(secretName)}' --query value -o tsv 2>"\$error_file")
      status=\$?

      if [ "\$status" -ne 0 ]; then
        cat "\$error_file" >&2
        rm -f "\$error_file"
        exit "\$status"
      fi

      rm -f "\$error_file"
      printf '%s' "\$secret_value"
    """.stripIndent().trim(),
    returnStdout: true
  )
}

private List<String> readLegacyKeyVaultSecrets(List<Map<String, Object>> secrets, String keyVaultUrl) {
  List<String> variables = []

  withAzureKeyvault(
    azureKeyVaultSecrets: secrets,
    keyVaultURLOverride: keyVaultUrl,
    applicationIDOverride: env.AZURE_CLIENT_ID,
    applicationSecretOverride: env.AZURE_CLIENT_SECRET
  ) {
    variables = secrets.collect { Map<String, Object> secret ->
      String envVariable = secret.envVariable.toString()

      "${envVariable}=${env[envVariable] ?: ''}"
    }
  }

  return variables
}

private boolean shouldRetryWithLegacySecretReader(String azureConfigName, String vaultName) {
  return azureConfigName == 'preview' && vaultName ==~ /.*-aat$/
}

private void loginWithManagedIdentity(String azureConfigDir) {
  sh(
    script: """
      set +x
      env AZURE_CONFIG_DIR='${shellQuote(azureConfigDir)}' az login --identity >/dev/null
    """.stripIndent().trim()
  )
}

private String vaultNameFromUrl(String keyVaultUrl) {
  return keyVaultUrl
    .replaceFirst(/^https:\/\//, '')
    .replaceFirst(/\.vault\.azure\.net\/?$/, '')
}

private String shellQuote(String value) {
  return value.replace("'", "'\"'\"'")
}
