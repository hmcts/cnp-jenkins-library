package uk.gov.hmcts.pipeline

import groovy.json.JsonOutput
import uk.gov.hmcts.contino.RepositoryUrl

import java.util.Locale

class CveDashboardSnapshotPublisher implements Serializable {
  private static final Map<String, Integer> SEVERITY_RANK = [LOW: 1, MEDIUM: 2, HIGH: 3, CRITICAL: 4]

  def steps

  CveDashboardSnapshotPublisher(steps) {
    this.steps = steps
  }

  def publishSnapshot(String codeBaseType, report) {
    try {
      if (!isPublishingBranch()) {
        return
      }

      String dashboardUrl = trimValue(steps.env.CVE_DASHBOARD_URL)
      String apiKey = trimValue(steps.env.CVE_DASHBOARD_API_KEY)

      if (!dashboardUrl || !apiKey) {
        return
      }

      def payload = buildPayload(codeBaseType, report)
      def endpoint = "${dashboardUrl.replaceAll('/+$', '')}/api/cves/snapshots"
      def requestId = payload.sourceRunId ?: UUID.randomUUID().toString()
      def response = steps.httpRequest(
        httpMode: 'POST',
        acceptType: 'APPLICATION_JSON',
        contentType: 'APPLICATION_JSON',
        url: endpoint,
        customHeaders: [
          [name: 'X-API-Key', value: apiKey, maskValue: true],
          [name: 'X-Request-Id', value: requestId]
        ],
        requestBody: JsonOutput.toJson(payload),
        validResponseCodes: '100:599'
      )

      Integer status = (response?.status ?: 0) as Integer
      if (status >= 400) {
        steps.echo "Unable to publish CVE dashboard snapshot '${status}'"
      }
    } catch (err) {
      steps.echo "Unable to publish CVE dashboard snapshot '${err}'"
    }
  }

  def buildPayload(String codeBaseType, report) {
    String gitUrl = trimValue(steps.env.GIT_URL)
    [
      team        : trimValue(steps.env.TEAM_NAME),
      repository  : repositoryName(gitUrl),
      codebaseType: codeBaseType,
      branchName  : trimValue(steps.env.BRANCH_NAME),
      gitUrl      : gitUrl,
      sourceSystem: 'jenkins',
      sourceRunId : trimValue(steps.env.BUILD_TAG) ?: trimValue(steps.env.BUILD_DISPLAY_NAME),
      sourceUrl   : trimValue(steps.env.BUILD_URL),
      items       : normaliseReport(codeBaseType, report)
    ]
  }

  private List<Map<String, Object>> normaliseReport(String codeBaseType, report) {
    def aggregate = [:]

    if (codeBaseType == 'node') {
      addYarnFindings(aggregate, report?.vulnerabilities, false)
      addYarnFindings(aggregate, report?.suppressed, true)
    } else {
      addGradleFindings(aggregate, report?.dependencies)
    }

    aggregate.values()
      .sort { it.cve }
      .collect { entry ->
        entry.suppressedPackages.removeAll(entry.activePackages)
        def item = [
          cve               : entry.cve,
          severity          : entry.severity.toLowerCase(Locale.ROOT),
          activePackages    : entry.activePackages.sort(),
          suppressedPackages: entry.suppressedPackages.sort()
        ]

        if (entry.score != null) {
          item.score = String.format(Locale.US, '%.1f', entry.score as BigDecimal)
        }

        item
      }
  }

  private void addYarnFindings(Map aggregate, findings, boolean suppressed) {
    asList(findings).each { finding ->
      String packageName = trimValue(finding?.module_name)
      asList(finding?.cves).each { cve ->
        addFinding(aggregate, cve, packageName, finding?.severity, finding?.cvss?.score, suppressed)
      }
    }
  }

  private void addGradleFindings(Map aggregate, dependencies) {
    asList(dependencies).each { dependency ->
      String packageName = gradlePackageName(dependency)

      asList(dependency?.vulnerabilities).each { vulnerability ->
        addFinding(
          aggregate,
          vulnerability?.name,
          packageName,
          vulnerability?.severity ?: vulnerability?.cvssv3?.baseSeverity ?: vulnerability?.cvssv2?.severity,
          highestScore(vulnerability),
          false
        )
      }

      asList(dependency?.suppressedVulnerabilities).each { vulnerability ->
        addFinding(
          aggregate,
          vulnerability?.name,
          packageName,
          vulnerability?.severity ?: vulnerability?.cvssv3?.baseSeverity ?: vulnerability?.cvssv2?.severity,
          highestScore(vulnerability),
          true
        )
      }
    }
  }

  private void addFinding(Map aggregate, cveValue, String packageName, severityValue, scoreValue, boolean suppressed) {
    String cve = trimValue(cveValue).toUpperCase(Locale.ROOT)
    if (!isRealCve(cve) || !packageName) {
      return
    }

    def entry = aggregate[cve]
    if (!entry) {
      entry = [
        cve               : cve,
        severity          : 'LOW',
        score             : null,
        activePackages    : [] as Set,
        suppressedPackages: [] as Set
      ]
      aggregate[cve] = entry
    }

    String severity = normaliseSeverity(severityValue)
    if (SEVERITY_RANK[severity] > SEVERITY_RANK[entry.severity]) {
      entry.severity = severity
    }

    BigDecimal score = numericScore(scoreValue)
    if (score != null && (entry.score == null || score > entry.score)) {
      entry.score = score
    }

    if (suppressed) {
      entry.suppressedPackages.add(packageName)
    } else {
      entry.activePackages.add(packageName)
    }
  }

  private String repositoryName(String gitUrl) {
    if (!gitUrl) {
      return ''
    }

    new RepositoryUrl().getShortWithoutOrgOrSuffix(gitUrl)
  }

  private static String gradlePackageName(dependency) {
    def packageId = asList(dependency?.packages).find { trimValue(it?.id) }?.id
    trimValue(packageId) ?: trimValue(dependency?.fileName)
  }

  private static BigDecimal highestScore(vulnerability) {
    [vulnerability?.cvssv3?.baseScore, vulnerability?.cvssv2?.score, vulnerability?.cvss?.score]
      .collect { numericScore(it) }
      .findAll { it != null }
      .max()
  }

  private static String normaliseSeverity(value) {
    String severity = trimValue(value).toUpperCase(Locale.ROOT)
    if (severity == 'MODERATE') {
      return 'MEDIUM'
    }

    SEVERITY_RANK.containsKey(severity) ? severity : 'LOW'
  }

  private static BigDecimal numericScore(value) {
    if (value == null) {
      return null
    }

    try {
      return new BigDecimal(value.toString())
    } catch (ignored) {
      return null
    }
  }

  private static boolean isRealCve(String value) {
    value ==~ /^CVE-\d{4}-\d{4,}$/
  }

  private boolean isPublishingBranch() {
    String branchName = trimValue(steps.env.BRANCH_NAME)
    branchName && allowedPublishingBranches().contains(branchName)
  }

  private List<String> allowedPublishingBranches() {
    def configured = trimValue(steps.env.CVE_DASHBOARD_PUBLISH_BRANCHES)
    if (!configured) {
      return ['master']
    }

    def branches = configured
      .split(',')
      .collect { trimValue(it) }
      .findAll { it }
      .unique()

    branches ?: ['master']
  }

  private static List asList(value) {
    value instanceof Collection ? value as List : []
  }

  private static String trimValue(value) {
    value == null ? '' : value.toString().trim()
  }
}
