package uk.gov.hmcts.pipeline

class AgentSelector implements Serializable {

  static final String DEFAULT_ENVIRONMENT_AGENT_LABEL_TEMPLATE = 'ubuntu-${environment}'

  static String labelForEnvironment(String environment, Object envVars = [:]) {
    String normalisedEnvironment = normaliseEnvironment(environment)
    if (!normalisedEnvironment) {
      return ''
    }

    String overrideKey = normalisedEnvironment.toUpperCase().replaceAll(/[^A-Z0-9]/, '_')
    // BUILD_AGENT_TYPE_* is supported as a transitional alias while Jenkins env vars are standardised.
    String environmentSpecificLabel = envValue(envVars, "ENVIRONMENT_AGENT_LABEL_${overrideKey}") ?:
      envValue(envVars, "BUILD_AGENT_TYPE_${overrideKey}")

    if (environmentSpecificLabel) {
      return environmentSpecificLabel
    }

    String labelTemplate = envValue(envVars, 'ENVIRONMENT_AGENT_LABEL_TEMPLATE') ?:
      DEFAULT_ENVIRONMENT_AGENT_LABEL_TEMPLATE

    return labelTemplate
      .replace('${environment}', normalisedEnvironment)
      .replace('{environment}', normalisedEnvironment)
  }

  static String normaliseEnvironment(String environment) {
    String cleanedEnvironment = environment?.trim()
      ?.replaceFirst(/^idam-/, '')
      ?.replaceFirst(/^packer-/, '')
      ?.replaceFirst(/^vault-/, '')

    switch (cleanedEnvironment) {
      case null:
      case '':
        return ''
      case 'sandbox':
        return 'sbox'
      default:
        return cleanedEnvironment
    }
  }

  private static String envValue(Object envVars, String key) {
    if (envVars == null) {
      return ''
    }

    try {
      def value = envVars[key]
      return value == null ? '' : value.toString().trim()
    } catch (ignored) {
      // Jenkins env can behave like a dynamic map; selector failures should fall back to defaults.
      return ''
    }
  }
}
