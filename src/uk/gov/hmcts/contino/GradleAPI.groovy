package uk.gov.hmcts.contino

class GradleAPI {

  def steps

  GradleAPI(steps) {
    this.steps = steps
  }

  String resolveGradleVersion() {
    try {
      return this.steps.sh(
        script: "./gradlew --no-daemon -q properties | grep '^version:' | cut -d ':' -f 2- | head -n 1",
        returnStdout: true
      ).trim()
    } catch (Exception ignored) {
      return null
    }
  }

  int compareReleaseVersions(String left, String right) {
    List<Integer> leftParts = versionNumericParts(left)
    List<Integer> rightParts = versionNumericParts(right)
    int maxParts = Math.max(leftParts.size(), rightParts.size())

    for (int i = 0; i < maxParts; i++) {
      int leftPart = i < leftParts.size() ? leftParts[i] : 0
      int rightPart = i < rightParts.size() ? rightParts[i] : 0
      if (leftPart != rightPart) {
        return leftPart <=> rightPart
      }
    }

    return 0
  }

  private List<Integer> versionNumericParts(String version) {
    String normalized = normalizeVersion(version)
    if (!normalized) {
      return [0]
    }

    return normalized
      .split('\\.')
      .findAll { it ==~ /\d+/ }
      .collect { it as Integer }
  }

  private String normalizeVersion(String version) {
    String cleaned = (version ?: '').trim()
    if (!cleaned) {
      return ''
    }

    cleaned = cleaned.replaceFirst('^[vV]', '')
    cleaned = cleaned.replaceFirst('[-+].*$', '')
    return cleaned
  }
}

