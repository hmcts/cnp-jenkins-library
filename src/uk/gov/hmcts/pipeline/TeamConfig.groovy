package uk.gov.hmcts.pipeline

class TeamConfig {

  def steps
  static final String DEFAULT_TEAM_NAME = 'pleaseTagMe'
  static final String NAMESPACE_KEY = "namespace"
  static final String CONTACT_SLACK_CHANNEL_KEY = "contact_channel"
  static final String BUILD_NOTICES_CHANNEL_KEY = "build_notices_channel"
  static final String TEAM_KEY = "team"
  static final String SLACK_KEY = "slack"
  static final String TAGS_KEY = "tags"
  static final String APPLICATION_KEY = "application"
  static final String AGENT_KEY = "agent"
  static final String DOCKER_AGENT_LABEL = "k8s-agent"
  static final String CONTAINER_AGENT = "inbound-agent"
  static final String REGISTRY_KEY = "registry"
  static def teamConfigMap

  TeamConfig(steps){
    this.steps = steps
  }

  def setTeamConfigEnv(String product){
    def teamNames = getTeamNamesMap()
    this.steps.env.TEAM_NAME = getName(product)
    this.steps.env.RAW_PRODUCT_NAME = getRawProductName(product)
    this.steps.env.TEAM_NAMESPACE = getNameSpace(product)
    this.steps.env.BUILD_NOTICES_SLACK_CHANNEL = getBuildNoticesSlackChannel(product)
    this.steps.env.CONTACT_SLACK_CHANNEL = getContactSlackChannel(product)
    this.steps.env.TEAM_CONTAINER_REGISTRY = getContainerRegistry(product)
    this.steps.env.TEAM_APPLICATION_TAG = getApplicationTag(product)
    this.steps.env.ARDOQ_APPLICATION_ID = getArdoqApplicationId(product)

    def buildAgentType = getBuildAgentType(product)
    this.steps.env.BUILD_AGENT_TYPE = buildAgentType
    this.steps.env.IS_DOCKER_BUILD_AGENT = isDockerBuildAgent(buildAgentType)
    this.steps.env.BUILD_AGENT_CONTAINER = getBuildAgentContainer(buildAgentType)
  }

  def getTeamNamesMap() {
    if (teamConfigMap ==null ){
      def repo = steps.env.JENKINS_CONFIG_REPO ?: "cnp-jenkins-config"

      def response = steps.httpRequest(
        consoleLogResponseBody: true,
        timeout: 10,
        url: "https://raw.githubusercontent.com/hmcts/${repo}/OPS/DTSPO-24729/team-config.yml",
        validResponseCodes: '200'
      )
      teamConfigMap = steps.readYaml (text: response.content)
    }
    return teamConfigMap
  }


  def getName (String product) {
    def teamNames = getTeamNamesMap()
    product = getRawProductName(product)
    if (!teamNames.containsKey(product)) {
      return DEFAULT_TEAM_NAME
    }
    return teamNames.get(product).get(TEAM_KEY,DEFAULT_TEAM_NAME)
  }

  def getRawProductName(String product) {
    return product.startsWith('pr-') ? product.split('pr-(\\d+)-')[1] : product
  }

  def getNameSpace(String product) {
    def teamNames = getTeamNamesMap()
    if (product.startsWith('pr-')) {
      product = getRawProductName(product)
    }
    if (!teamNames.containsKey(product) || !teamNames.get(product).get(NAMESPACE_KEY)) {
      def repo = steps.env.JENKINS_CONFIG_REPO ?: "cnp-jenkins-config"

      steps.error ("Product ${product} does not belong to any team. "
          + "Please create a PR to update TeamConfig in ${repo}.")
    }
    return teamNames.get(product).get(NAMESPACE_KEY)
      .toLowerCase()
      .replace("/", "-")
      .replace(" ", "-")
  }

  def getDefaultTeamSlackChannel(String product, String key) {
    def teamNames = getTeamNamesMap()
    if (!teamNames.containsKey(product) || !teamNames.get(product).get(SLACK_KEY) || !teamNames.get(product).get(SLACK_KEY).get(key)) {
      def repo = steps.env.JENKINS_CONFIG_REPO ?: "cnp-jenkins-config"

      steps.error ("${key} is not configured for Product ${product} ."
          + "Please create a PR to update team-config.yml in ${repo}.")
    }
    return teamNames.get(product).get(SLACK_KEY).get(key)
  }

  def getBuildNoticesSlackChannel(String product) {
      String slackChannel = this.steps.env.BUILD_NOTICE_SLACK_CHANNEL
      return slackChannel != null && !slackChannel.isEmpty() ? slackChannel : getDefaultTeamSlackChannel(product,BUILD_NOTICES_CHANNEL_KEY)
  }

  def getContactSlackChannel(String product) {
    return getDefaultTeamSlackChannel(getRawProductName(product),CONTACT_SLACK_CHANNEL_KEY)
  }

  def getArdoqApplicationId(String product) {
    def teamNames = getTeamNamesMap()
    if (teamNames?.get(product)?.get('ardoq')?.get('application_id')) {
      return teamNames.get(product).get('ardoq').get('application_id')
    }
    steps.echo("Ardoq Application Id not set")
  }

  String getBuildAgentType(String product) {
    def teamNames = getTeamNamesMap()
    def rawProductName = getRawProductName(product)
    if (!teamNames.containsKey(rawProductName) || !teamNames.get(rawProductName).get(AGENT_KEY)) {
      steps.echo("Agent type not found. Using default agent")
      return ""
    }
    return teamNames.get(rawProductName).get(AGENT_KEY)
  }

  boolean isDockerBuildAgent(String agentLabel) {
    return agentLabel == DOCKER_AGENT_LABEL
  }

  String getBuildAgentContainer(String agentLabel) {
    return isDockerBuildAgent(agentLabel) ? CONTAINER_AGENT : ""
  }

  String getContainerRegistry(String product) {
    def teamNames = getTeamNamesMap()
    return teamNames.containsKey(product) && teamNames.get(product).get(REGISTRY_KEY) ? teamNames.get(product).get(REGISTRY_KEY) : ""
  }

  String getApplicationTag(String product) {
    def teamNames = getTeamNamesMap()
    if (!teamNames.containsKey(product) || !teamNames.get(product).get(TAGS_KEY) || !teamNames.get(product).get(TAGS_KEY).get(APPLICATION_KEY)) {
      def repo = steps.env.JENKINS_CONFIG_REPO ?: "cnp-jenkins-config"

      steps.error ("${APPLICATION_KEY} tag is not configured for Product ${product} ."
        + "Please create a PR to update team-config.yml in ${repo}.")
    }
    return teamNames.get(product).get(TAGS_KEY).get(APPLICATION_KEY)
  }

}
