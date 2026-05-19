package uk.gov.hmcts.pipeline

class AgentSelector implements Serializable {

  static final String DEFAULT_ENVIRONMENT_AGENT_LABEL_TEMPLATE = 'ubuntu-${environment}'
  private static final Set<String> ENVIRONMENT_LIKE_SUBSCRIPTIONS = ['dev', 'stg', 'prod', 'sbox'] as Set

  static String labelForEnvironment(String environment, Object envVars = [:], String product = '') {
    return selectLabelForEnvironment(environment, envVars, product, true)
  }

  static String labelForEnvironmentWithoutProductFallback(String environment, Object envVars = [:]) {
    return selectLabelForEnvironment(environment, envVars, '', false)
  }

  static boolean isEnvironmentLikeSubscription(String subscription) {
    return ENVIRONMENT_LIKE_SUBSCRIPTIONS.contains(normaliseEnvironment(subscription))
  }

  static boolean isRunningOnEnvironmentAgent(Object envVars, String environment = null, String product = '') {
    String currentEnvironment = environment ?: envValue(envVars, 'DEPLOYMENT_ENVIRONMENT')
    if (!currentEnvironment) {
      return false
    }

    return envValue(envVars, 'BUILD_AGENT_TYPE') == labelForEnvironment(currentEnvironment, envVars, product)
  }

  private static String selectLabelForEnvironment(String environment, Object envVars, String product, boolean allowProductFallback) {
    String normalisedEnvironment = normaliseEnvironment(environment)
    if (!normalisedEnvironment) {
      return ''
    }

    String overrideKey = normalisedEnvironment.toUpperCase().replaceAll(/[^A-Z0-9]/, '_')
    String productKey = allowProductFallback ?
      normaliseProduct(product ?: envValue(envVars, 'PRODUCT') ?: envValue(envVars, 'RAW_PRODUCT_NAME')) :
      ''

    if (productKey) {
      String productEnvironmentSpecificLabel = envValue(envVars, "ENVIRONMENT_AGENT_LABEL_${productKey}_${overrideKey}")
      if (productEnvironmentSpecificLabel) {
        return productEnvironmentSpecificLabel
      }

      String productLabelTemplate = envValue(envVars, "ENVIRONMENT_AGENT_LABEL_TEMPLATE_${productKey}")
      if (productLabelTemplate) {
        return renderLabel(productLabelTemplate, normalisedEnvironment)
      }

      String productAgentLabel = envValue(envVars, 'PRODUCT_AGENT_LABEL')
      if (productAgentLabel) {
        return productAgentLabel
      }
    }

    // BUILD_AGENT_TYPE_* is supported as a transitional alias while Jenkins env vars are standardised.
    String environmentSpecificLabel = envValue(envVars, "ENVIRONMENT_AGENT_LABEL_${overrideKey}") ?:
      envValue(envVars, "BUILD_AGENT_TYPE_${overrideKey}")

    if (environmentSpecificLabel) {
      return environmentSpecificLabel
    }

    String labelTemplate = envValue(envVars, 'ENVIRONMENT_AGENT_LABEL_TEMPLATE') ?:
      DEFAULT_ENVIRONMENT_AGENT_LABEL_TEMPLATE

    return renderLabel(labelTemplate, normalisedEnvironment)
  }

  private static String renderLabel(String labelTemplate, String normalisedEnvironment) {
    return labelTemplate
      .replace('${environment}', normalisedEnvironment)
      .replace('{environment}', normalisedEnvironment)
  }

  static String normaliseEnvironment(String environment) {
    String cleanedEnvironment = cleanedEnvironment(environment)

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

  static String managedIdentityResourceGroupEnvironment(String environment) {
    String cleanedEnvironment = cleanedEnvironment(environment)
    // The CFT sandbox MI RG is provisioned with the literal subscription alias,
    // while the MI itself still uses the normalised sbox identity name.
    return cleanedEnvironment == 'sbox' ? 'sandbox' : cleanedEnvironment
  }

  private static String cleanedEnvironment(String environment) {
    return environment?.trim()
      ?.replaceFirst(/^idam-/, '')
      ?.replaceFirst(/^packer-/, '')
      ?.replaceFirst(/^vault-/, '')
  }

  private static String normaliseProduct(String product) {
    return product?.trim()?.toUpperCase()?.replaceAll(/[^A-Z0-9]/, '_') ?: ''
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
