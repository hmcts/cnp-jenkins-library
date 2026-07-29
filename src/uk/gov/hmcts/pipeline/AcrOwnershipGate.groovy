package uk.gov.hmcts.pipeline

class AcrOwnershipGate implements Serializable {
  static final Set<String> VALID_MODES = ['off', 'audit', 'enforce'] as Set

  Map<String, Object> evaluate(String mode, String product, String component, String repository, List<String> allowList = []) {
    String normalizedMode = normalizeMode(mode)
    String rawProduct = normalizeProduct(product)
    String repositoryDestination = stripTag(repository)

    if (normalizedMode == 'off') {
      return decision(normalizedMode, rawProduct, component, repository, 'allow', 'MODE_OFF_BYPASS', false, false)
    }

    if (!rawProduct || !component || !repositoryDestination) {
      return decision(normalizedMode, rawProduct, component, repository, 'deny', 'INVALID_INPUT', false, false)
    }

    String escapedProduct = java.util.regex.Pattern.quote(rawProduct.toLowerCase())
    String repoRegex = '^[a-z0-9._-]*' + escapedProduct + '[a-z0-9._-]*/[a-z0-9._-]+$'

    boolean regexMatched = repositoryDestination.toLowerCase() ==~ repoRegex
    boolean allowListMatched = isAllowListMatch(repositoryDestination, allowList ?: [])

    if (regexMatched) {
      return decision(normalizedMode, rawProduct, component, repository, 'allow', 'REGEX_MATCH', true, allowListMatched)
    }

    if (allowListMatched) {
      return decision(normalizedMode, rawProduct, component, repository, 'allow', 'ALLOWLIST_MATCH', false, true)
    }

    return decision(normalizedMode, rawProduct, component, repository, 'deny', 'REGEX_MISS', false, false)
  }

  boolean shouldBlock(Map<String, Object> decision) {
    return decision?.mode == 'enforce' && decision?.decision == 'deny'
  }

  String logLine(Map<String, Object> decision) {
    return "ACR_OWNERSHIP_GATE mode=${decision.mode} product=${decision.product} component=${decision.component} repository=${decision.repository} decision=${decision.decision} reasonCode=${decision.reasonCode} regexMatched=${decision.regexMatched} allowListMatched=${decision.allowListMatched}"
  }

  String normalizeMode(String mode) {
    String normalized = mode?.trim()?.toLowerCase()
    return VALID_MODES.contains(normalized) ? normalized : 'off'
  }

  String normalizeProduct(String product) {
    if (!product) {
      return product
    }

    def matcher = (product =~ /^pr-\d+-(.+)$/)
    return matcher ? matcher[0][1] : product
  }

  private String stripTag(String repository) {
    if (!repository) {
      return repository
    }

    return repository.contains(':') ? repository.split(':')[0] : repository
  }

  private boolean isAllowListMatch(String repositoryDestination, List<String> allowList) {
    if (!repositoryDestination) {
      return false
    }

    return allowList.any { allowItem ->
      if (!allowItem) {
        return false
      }

      String normalized = allowItem.trim()
      return normalized == repositoryDestination
    }
  }

  private Map<String, Object> decision(String mode, String product, String component, String repository, String result, String reasonCode, boolean regexMatched, boolean allowListMatched) {
    return [
      mode: mode,
      product: product,
      component: component,
      repository: repository,
      decision: result,
      reasonCode: reasonCode,
      regexMatched: regexMatched,
      allowListMatched: allowListMatched
    ]
  }
}
