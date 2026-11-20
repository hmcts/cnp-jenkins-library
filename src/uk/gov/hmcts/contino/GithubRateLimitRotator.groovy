package uk.gov.hmcts.contino

class GithubRateLimitRotator {

  private static final Map<String, List<String>> POOLS = [
    'tag-verification': [
      'hmcts-jenkins-tag-verification',
      'hmcts-jenkins-tag-verification-1',
      'hmcts-jenkins-tag-verification-2'
    ],
    'allowed-branches': [
      'hmcts-jenkins-allowed-branches',
      'hmcts-jenkins-allowed-branches-1',
      'hmcts-jenkins-allowed-branches-2'
    ],
    'a-to-c': [
      'hmcts-jenkins-a-to-c',
      'hmcts-jenkins-a-to-c-2',
      'hmcts-jenkins-a-to-c-3'
    ],
    'd-to-i': [
      'hmcts-jenkins-d-to-i',
      'hmcts-jenkins-d-to-i-2',
      'hmcts-jenkins-d-to-i-3'
    ],
    'j-to-z': [
      'hmcts-jenkins-j-to-z',
      'hmcts-jenkins-j-to-z-2',
      'hmcts-jenkins-j-to-z-3'
    ]
  ]

  private final def steps

  GithubRateLimitRotator(def steps) {
    this.steps = steps
    for (List<String> pool : POOLS.values()) {
      verifyPool(pool)
    }
  }

  def httpRequest(String credentialsId, Map params) {
    if (!credentialsId) {
      throw new IllegalArgumentException('A GitHub credential ID is required')
    }

    List<String> pool = resolvePool(credentialsId)
    if (pool.isEmpty()) {
      return execute(credentialsId, params)
    }

    int startIndex = pool.indexOf(credentialsId)

    for (int i = startIndex; i < pool.size(); i++) {
      String candidate = pool[i]
      try {
        def response = execute(candidate, params)
        if (isCoreRateLimitExhausted(response)) {
          boolean allowRetry = isSafeRetry(params)
          if (!allowRetry) {
            throw httpError(response)
          }
          continue
        }
        if (isHttpError(response)) {
          if (isStatusAllowed(response.status, params.validResponseCodes)) {
            return response
          }
          throw httpError(response)
        }
        return response
      } catch (RuntimeException err) {
        throw err
      } catch (Throwable err) {
        throw err
      }
    }
    throw new IllegalStateException("GitHub credential pool exhausted for '${credentialsId}'")
  }

  List<String> resolvePool(String credentialsId) {
    for (List<String> pool : getPools().values()) {
      if (pool.contains(credentialsId)) {
        return pool
      }
    }
    return []
  }

  Map<String, List<String>> getPools() {
    return POOLS.collectEntries { key, value -> [key, Collections.unmodifiableList(new ArrayList<String>(value))] }
  }

  private def execute(String credentialsId, Map params) {
    Map request = new LinkedHashMap(params)
    request.authentication = credentialsId
    request.validResponseCodes = '100:599'
    return this.steps.httpRequest(request)
  }

  private void verifyPool(List<String> pool) {
    for (String credentialId : pool) {
      try {
        def credential = this.steps.usernamePassword(
          credentialsId: credentialId,
          usernameVariable: 'GITHUB_RATE_LIMIT_ROTATOR_APP_ID',
          passwordVariable: 'GITHUB_RATE_LIMIT_ROTATOR_TOKEN'
        )
        this.steps.withCredentials([credential]) {
          // Credential resolution is deliberately verified without making a GitHub request.
        }
      } catch (Throwable err) {
        throw new IllegalStateException(
          "GitHub credential '${credentialId}' in pool is missing or cannot be bound",
          err
        )
      }
    }
  }

  private boolean isHttpError(def response) {
    if (response == null) {
      return false
    }
    try {
      int status = response.status as int
      return status >= 400
    } catch (Exception ignored) {
      return false
    }
  }

  private boolean isCoreRateLimitExhausted(def response) {
    if (response == null || !isHttpError(response)) {
      return false
    }

    int status
    try {
      status = response.status as int
    } catch (Exception ignored) {
      return false
    }

    if (status != 403 && status != 429) {
      return false
    }

    if (headerValue(response, 'x-ratelimit-remaining') != '0') {
      return false
    }

    String resource = headerValue(response, 'x-ratelimit-resource')
    return !resource || resource.equalsIgnoreCase('core')
  }

  private boolean isSafeRetry(Map params) {
    String method = (params.httpMode ?: 'GET').toString().toUpperCase()
    return method in ['GET', 'HEAD']
  }


  private String headerValue(def response, String name) {
    def headers = response.headers
    if (headers instanceof Map) {
      for (entry in headers.entrySet()) {
        if (entry.key?.toString()?.equalsIgnoreCase(name)) {
          def value = entry.value
          if (value instanceof Collection) {
            return value ? value.first()?.toString()?.trim() : null
          }
          if (value != null) {
            return value.toString().trim()
          }
        }
      }
    }
    return null
  }

  private boolean isStatusAllowed(def status, def validResponseCodes) {
    if (validResponseCodes == null) {
      return (status as int) >= 200 && (status as int) < 300
    }
    for (String part : validResponseCodes.toString().split(',')) {
      String value = part.trim()
      if (!value) {
        continue
      }
      if (value.contains(':')) {
        def range = value.split(':')
        int start = range[0] as int
        int end = range[1] as int
        if ((status as int) >= start && (status as int) <= end) {
          return true
        }
      } else if ((status as int) == (value as int)) {
        return true
      }
    }
    return false
  }

  private RuntimeException httpError(def response) {
    String resource = headerValue(response, 'x-ratelimit-resource')
    String remaining = headerValue(response, 'x-ratelimit-remaining')
    return new RuntimeException(
      "GitHub HTTP request failed with status ${response.status}" +
        (resource ? " for resource '${resource}'" : '') +
        (remaining != null ? " with remaining=${remaining}" : '')
    )
  }
}
