package uk.gov.hmcts.contino

import java.util.regex.Pattern

class IPV4Validator {
  private static final String IPV4_REGEX = /^((0|1\d?\d?|2[0-4]?\d?|25[0-5]?|[3-9]\d?)\.){3}(0|1\d?\d?|2[0-4]?\d?|25[0-5]?|[3-9]\d?)$/
  private static final Pattern IPV4_PATTERN = Pattern.compile(IPV4_REGEX)

  static boolean validate(String ipAddress) {
    if (ipAddress == null || ipAddress.isEmpty()) {
      return false
    }

    return IPV4_PATTERN.matcher(ipAddress).matches()
  }
}
