package uk.gov.hmcts.contino

class CosmosDbTargetResolver implements Serializable {

  static final String DEFAULT_DATABASE = "jenkins"
  // When topics include "jenkins-sds" (case-insensitive) use "sds-jenkins" as the database name.
  static final String SDS_TOPIC = "jenkins-sds"
  static final String SDS_DATABASE = "sds-jenkins"

  private final def steps

  CosmosDbTargetResolver(steps) {
    this.steps = steps
  }

  String databaseName() {
    String topicsText
    try {
      topicsText = fetchTopicsText()
    } catch (Exception ex) {
      steps.echo("Cosmos DB: unable to fetch repository topics, defaulting to '${DEFAULT_DATABASE}': ${ex.message}")
      topicsText = ""
    }

    def topicsLower = topicsText?.toLowerCase() ?: ""

    if (topicsLower.contains(SDS_TOPIC)) {
      steps.echo("Cosmos DB: detected SDS repository topic '${SDS_TOPIC}'; using database '${SDS_DATABASE}'")
      return SDS_DATABASE
    }

    return DEFAULT_DATABASE
  }

  protected String fetchTopicsText() {
    def topics = new GithubAPI(steps).refreshTopicCache()
    return topics?.toString() ?: ""
  }
}
