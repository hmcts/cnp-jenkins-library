package uk.gov.hmcts.contino

class CosmosDbTargetResolver implements Serializable {

  static final String DEFAULT_DATABASE = "jenkins"
  static final String DATABASE_ENV_VAR = "PIPELINE_METRICS_DATABASE"

  private final def steps

  CosmosDbTargetResolver(steps) {
    this.steps = steps
  }

  String databaseName() {
    String database = steps.env?.PIPELINE_METRICS_DATABASE?.toString()?.trim()
    return database ?: DEFAULT_DATABASE
  }
}
