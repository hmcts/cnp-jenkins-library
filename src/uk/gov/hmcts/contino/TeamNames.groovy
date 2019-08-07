package uk.gov.hmcts.contino

class TeamNames {

  def steps
  static final String GITHUB_CREDENTIAL = 'jenkins-github-hmcts-api-token'
  static final String DEFAULT_TEAM_NAME = 'pleaseTagMe'
  static final String NAMESPACE_KEY = "namespace"
  static final String DEFAULT_SLACK_CHANNEL_KEY = "defaultSlackChannel"
  static final String TEAM_KEY = "team"
  static def teamNamesMap

  TeamNames (steps){
    this.steps = steps
  }

  def getTeamNamesMap() {
    if (teamNamesMap ==null ){
      def response = steps.httpRequest(
        consoleLogResponseBody: true,
        authentication: "${GITHUB_CREDENTIAL}",
        timeout: 10,
        url: "https://raw.githubusercontent.com/hmcts/cnp-jenkins-config/master/team-config.yml",
        validResponseCodes: '200'
      )
      teamNamesMap = steps.readYaml (text: response.content)
    }
    return teamNamesMap
  }


  def getName (String product) {
    def teamNames = getTeamNamesMap()
    if (product.startsWith('pr-')) {
      product = getRawProductName(product)
    }
    if (!teamNames.containsKey(product)) {
      return DEFAULT_TEAM_NAME
    }
    return teamNames.get(product).get(TEAM_KEY,DEFAULT_TEAM_NAME)
  }

  def getRawProductName (String product) {
    return product.split('pr-(\\d+)-')[1]
  }

  def getNameSpace(String product) {
    def teamNames = getTeamNamesMap()
    if (product.startsWith('pr-')) {
      product = getRawProductName(product)
    }
    if (!teamNames.containsKey(product) || !teamNames.get(product).get(NAMESPACE_KEY)) {
      throw new RuntimeException(
        "Product ${product} does not belong to any team. "
          + "Please create a PR to update TeamNames in cnp-jenkins-config."
      )
    }
    return teamNames.get(product).get(NAMESPACE_KEY)
      .toLowerCase()
      .replace("/", "-")
      .replace(" ", "-")
  }

  def getDefaultSlackChannel(String product) {
    def teamNames = getTeamNamesMap()
    if (!teamNames.containsKey(product) || !teamNames.get(product).get(DEFAULT_SLACK_CHANNEL_KEY) || teamNames.get(product).get(DEFAULT_SLACK_CHANNEL_KEY).isEmpty()) {
      throw new RuntimeException(
        "defaultSlackChannel is not configured for Product ${product}"
          + "Please create a PR to update team-config.yml in cnp-jenkins-config."
      )
    }
    return teamNames.get(product).get(DEFAULT_SLACK_CHANNEL_KEY)
  }

  def getSlackChannel(String product, String slackChannel) {
      return slackChannel!=null && !slackChannel.isEmpty() ? slackChannel : getDefaultSlackChannel(product)
  }

}
