package uk.gov.hmcts.pipeline

class AcrOwnershipPolicyConfig implements Serializable {
  def steps
  List<String> approvedJenkinsConfigRepos
  boolean warnOnUnapprovedJenkinsConfigRepo
  static final String DEFAULT_JENKINS_CONFIG_REPO = 'cnp-jenkins-config'
  static final String POLICY_FILE_NAME = 'team-repo-allowlist.yaml'
  static final Set<String> VALID_MODES = ['off', 'audit', 'enforce'] as Set
  static def ownershipPolicyMap

  AcrOwnershipPolicyConfig(steps, List<String> approvedJenkinsConfigRepos = [DEFAULT_JENKINS_CONFIG_REPO], boolean warnOnUnapprovedJenkinsConfigRepo = true) {
    this.steps = steps
    this.approvedJenkinsConfigRepos = approvedJenkinsConfigRepos ?: [DEFAULT_JENKINS_CONFIG_REPO]
    this.warnOnUnapprovedJenkinsConfigRepo = warnOnUnapprovedJenkinsConfigRepo
  }

  Map<String, Object> getOwnershipPolicy(String product) {
    String rawProduct = normalizeProduct(product)

    try {
      def policy = getPolicyMap()
      String globalMode = normalizeMode(policy?.mode)
      String mode = resolveMode(policy, rawProduct, globalMode)
      List<String> allowList = resolveAllowList(policy, rawProduct)

      return [
        mode: mode,
        allowList: allowList,
        warningMessage: null
      ]
    } catch (Exception ex) {
      String repo = steps.env.JENKINS_CONFIG_REPO ?: DEFAULT_JENKINS_CONFIG_REPO
      return [
        mode: 'off',
        allowList: [],
        warningMessage: "Warning: Failed to load ${POLICY_FILE_NAME} from ${repo}. Falling back to mode=off. Cause: ${ex.message}"
      ]
    }
  }

  private def getPolicyMap() {
    if (ownershipPolicyMap == null) {
      String repo = steps.env.JENKINS_CONFIG_REPO ?: DEFAULT_JENKINS_CONFIG_REPO
      warnIfRepoUnapproved(repo)

      def response = steps.httpRequest(
        consoleLogResponseBody: true,
        timeout: 10,
        url: "https://raw.githubusercontent.com/hmcts/${repo}/master/${POLICY_FILE_NAME}",
        validResponseCodes: '200'
      )

      ownershipPolicyMap = steps.readYaml(text: response.content)
    }

    return ownershipPolicyMap
  }

  private void warnIfRepoUnapproved(String repo) {
    if (!warnOnUnapprovedJenkinsConfigRepo) {
      return
    }

    if (!(approvedJenkinsConfigRepos ?: []).contains(repo)) {
      steps.echo("Warning: JENKINS_CONFIG_REPO '${repo}' is not in approved list ${approvedJenkinsConfigRepos}")
    }
  }

  private String normalizeMode(Object mode) {
    String normalized = mode?.toString()?.trim()?.toLowerCase()
    return VALID_MODES.contains(normalized) ? normalized : 'off'
  }

  private String normalizeProduct(String product) {
    if (!product) {
      return product
    }

    def matcher = (product =~ /^pr-\d+-(.+)$/)
    return matcher ? matcher[0][1] : product
  }

  private List<String> resolveAllowList(def policy, String product) {
    def allowListSection = policy?.allow_list ?: policy?.allowList

    if (!allowListSection) {
      return []
    }

    List<String> collected = []

    if (allowListSection instanceof List) {
      collected.addAll(allowListSection)
      return normalizeList(collected)
    }

    if (!(allowListSection instanceof Map)) {
      return []
    }

    collected.addAll(asList(allowListSection.global))
    collected.addAll(asList(allowListSection.all))

    if (allowListSection.products instanceof Map) {
      collected.addAll(resolveProductAllowList(allowListSection.products[product]))
    }

    collected.addAll(resolveProductAllowList(allowListSection[product]))

    return normalizeList(collected)
  }

  private List<String> asList(def value) {
    if (!value) {
      return []
    }

    if (value instanceof List) {
      return value
    }

    return [value]
  }

  private String resolveMode(def policy, String product, String globalMode) {
    def allowListSection = policy?.allow_list ?: policy?.allowList

    if (!(allowListSection instanceof Map)) {
      return globalMode
    }

    def products = allowListSection.products
    if (!(products instanceof Map)) {
      return globalMode
    }

    def productPolicy = products[product]
    if (!(productPolicy instanceof Map)) {
      return globalMode
    }

    String productMode = productPolicy.mode?.toString()?.trim()?.toLowerCase()
    return VALID_MODES.contains(productMode) ? productMode : globalMode
  }

  private List<String> resolveProductAllowList(def productPolicy) {
    if (!productPolicy) {
      return []
    }

    if (productPolicy instanceof List) {
      return asList(productPolicy)
    }

    if (!(productPolicy instanceof Map)) {
      return asList(productPolicy)
    }

    if (productPolicy.repositories instanceof List) {
      return productPolicy.repositories
    }

    if (productPolicy.allow_list instanceof List) {
      return productPolicy.allow_list
    }

    if (productPolicy.allowList instanceof List) {
      return productPolicy.allowList
    }

    return []
  }

  private List<String> normalizeList(List<String> values) {
    return (values ?: [])
      .findAll { it != null }
      .collect { it.toString().trim() }
      .findAll { !it.isEmpty() }
  }
}
