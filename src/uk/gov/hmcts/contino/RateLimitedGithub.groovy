package uk.gov.hmcts.contino

class RateLimitedGithub implements Serializable {

  // List of various Github Apps to distribute requests across, these must exist as credentials within Jenkins!
  private static final Map<String, List<String>> POOLS = [
    'tag-verification': [
      'hmcts-jenkins-tag-verification',
      'hmcts-jenkins-tag-verification-2',
      'hmcts-jenkins-tag-verification-3'
    ],
    'allowed-branches': [
      'hmcts-jenkins-allowed-branches',
      'hmcts-jenkins-allowed-branches-2',
      'hmcts-jenkins-allowed-branches-3'
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

  RateLimitedGithub(def steps) {
    this.steps = steps
  }

  String getDistributedCredentialId(String credentialsId) {
    List<String> pool = resolvePool(credentialsId)
    if (pool.isEmpty()) {
      this.steps?.echo("Selected Github App credential: '${credentialsId}'")
      return credentialsId
    }
    String selectedCredentialId = pool[choosePrimaryOrSecondary(credentialsId)]
    this.steps?.echo("Selected Github App credential: '${selectedCredentialId}'")
    return selectedCredentialId
  }

  protected int choosePrimaryOrSecondary(String credentialsId) {
    String key = this.steps?.env?.GIT_URL ?
      this.steps.env.GIT_URL.toString() :
      (this.steps?.env?.JOB_NAME ?: credentialsId).toString()
    return new BigInteger(
      java.security.MessageDigest.getInstance('SHA-256')
        .digest(key.getBytes('UTF-8'))
    ).abs().mod(BigInteger.valueOf(2)).intValue()
  }

  def getWithDistributedCredentials(String credentialsId, Map params) {
    return request(credentialsId, params, 'GET', true)
  }

  def headWithDistributedCredentials(String credentialsId, Map params) {
    return request(credentialsId, params, 'HEAD', true)
  }

  def postWithDistributedCredentials(String credentialsId, Map params) {
    return request(credentialsId, params, 'POST', false)
  }

  def putWithDistributedCredentials(String credentialsId, Map params) {
    return request(credentialsId, params, 'PUT', false)
  }

  def patchWithDistributedCredentials(String credentialsId, Map params) {
    return request(credentialsId, params, 'PATCH', false)
  }

  def deleteWithDistributedCredentials(String credentialsId, Map params) {
    return request(credentialsId, params, 'DELETE', false)
  }

  private def request(String credentialsId, Map params, String method, boolean allowRetry) {
    List<String> pool = resolvePool(credentialsId)
    String selected = getDistributedCredentialId(credentialsId)
    Map request = new LinkedHashMap(params)
    request.httpMode = method
    request.authentication = selected
    if (!allowRetry || pool.isEmpty()) {
      def response = this.steps.httpRequest(request)
      if (isHttpError(response) && params.validResponseCodes && isStatusAllowed(response.status, params.validResponseCodes)) {
        return response
      }
      if (isHttpError(response) && params.validResponseCodes) {
        throw httpError(response)
      }
      return response
    }

    request.validResponseCodes = '100:599'
    def response = this.steps.httpRequest(request)
    if (isCoreRateLimitExhausted(response)) {
      request.authentication = pool[2]
      response = this.steps.httpRequest(request)
    }
    if (isHttpError(response) && !isStatusAllowed(response.status, params.validResponseCodes)) {
      throw httpError(response)
    }
    return response
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
