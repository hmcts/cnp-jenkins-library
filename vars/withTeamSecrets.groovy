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
  String fallbackAzureConfigDir = "/opt/jenkins/.azure-aat"

  loginWithManagedIdentity(azureConfigDir)
  boolean fallbackLoggedIn = false

  List<String> secretEnvVars = secrets.collect { Map<String, Object> secret ->
    String secretName = secret.name.toString()
    String envVariable = secret.envVariable.toString()
    String forbiddenSentinel = keyVaultForbiddenSentinel()
    boolean retryWithAat = shouldRetryWithAatSecretReader(azureConfigName, vaultName)
    String secretValue = readKeyVaultSecret(vaultName, secretName, azureConfigDir, forbiddenSentinel, retryWithAat).trim()

    if (secretValue == forbiddenSentinel && retryWithAat) {
      if (!fallbackLoggedIn) {
        echo "Preview Jenkins MI cannot read ${vaultName}; retrying with AAT Jenkins MI"
        loginWithManagedIdentity(fallbackAzureConfigDir)
        fallbackLoggedIn = true
      }
      String fallbackForbiddenSentinel = keyVaultForbiddenSentinel()
      secretValue = readKeyVaultSecret(vaultName, secretName, fallbackAzureConfigDir, fallbackForbiddenSentinel, true).trim()

      if (secretValue == fallbackForbiddenSentinel) {
        throw new RuntimeException("Preview Jenkins MI was denied access to ${vaultName}/${secretName}; AAT Jenkins MI retry was also denied. Check Key Vault access policies.")
      }
    }

    if (secretValue == forbiddenSentinel) {
      throw new RuntimeException("Key Vault secret ${vaultName}/${secretName} returned Forbidden and no retry applies")
    }

    [envVariable: envVariable, value: secretValue]
  }

  List<String> variables = secretEnvVars.collect { Map<String, String> secret ->
    "${secret.envVariable}=${secret.value}"
  }

  // Team secrets are expected to be single-line values. If a future consumer
  // needs PEM/RSA-style multi-line secrets, pass a file path instead.
  withEnv(variables) {
    body.call()
  }
}

private void loginWithManagedIdentity(String azureConfigDir) {
  sh(
    script: """
      set +x
      env AZURE_CONFIG_DIR='${shellQuote(azureConfigDir)}' az login --identity >/dev/null
    """.stripIndent().trim()
  )
}

private String readKeyVaultSecret(String vaultName, String secretName, String azureConfigDir, String failureSentinel, boolean returnFailureSentinel) {
  sh(
    label: "az keyvault secret show ${vaultName}/${secretName}",
    script: """
      set +x
      error_file=\$(mktemp)
      secret_value=\$(env AZURE_CONFIG_DIR='${shellQuote(azureConfigDir)}' az keyvault secret show --vault-name '${shellQuote(vaultName)}' --name '${shellQuote(secretName)}' --query value -o tsv 2>"\$error_file")
      status=\$?

      if [ "\$status" -ne 0 ]; then
        if ${returnFailureSentinel ? 'true' : 'false'}; then
          rm -f "\$error_file"
          echo '${shellQuote(failureSentinel)}'
          exit 0
        fi

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

private boolean shouldRetryWithAatSecretReader(String azureConfigName, String vaultName) {
  return azureConfigName == 'preview' && vaultName ==~ /.*-aat$/
}

private String keyVaultForbiddenSentinel() {
  return "__HMCTS_KEYVAULT_FORBIDDEN_${java.util.UUID.randomUUID()}__"
}

private String vaultNameFromUrl(String keyVaultUrl) {
  return keyVaultUrl
    .replaceFirst(/^https:\/\//, '')
    .replaceFirst(/\.vault\.azure\.net\/?$/, '')
}

private String shellQuote(String value) {
  return value.replace("'", "'\"'\"'")
}
