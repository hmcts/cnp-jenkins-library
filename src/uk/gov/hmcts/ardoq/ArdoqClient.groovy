package uk.gov.hmcts.ardoq

import uk.gov.hmcts.contino.RepositoryUrl

class ArdoqClient {
  String apiKey
  String apiUrl
  public steps

  ArdoqClient(apiKey, apiUrl, steps) {
    this.apiKey = apiKey
    this.apiUrl = apiUrl
    this.steps = steps
  }

  String getApplicationId() {
    def applicationId = this.steps.env.ARDOQ_APPLICATION_ID
    if (!applicationId?.trim()) {
      this.steps.echo "Ardoq Application Id is not configured for ${this.steps.env.GIT_URL}"
      return
    }
    return applicationId?.trim();
  }

  String getRepositoryName() {
    return new RepositoryUrl().getShortWithoutOrgOrSuffix(this.steps.env.GIT_URL)
  }

  String getLanguage() {
    if (!this.steps.fileExists('Dockerfile')) {
      this.steps.echo "No Dockerfile found, skipping tech stack maintenance"
      return
    }
    steps.sh "grep -E '^FROM' Dockerfile | awk '{print \$2}' | awk -F ':' '{printf(\"%s\", \$1)}' | tr '/' '\\n' | tail -1 > languageProc"
    def result = steps.readFile('languageProc')
    return result
  }

  String getLanguageVersion() {
    if (!this.steps.fileExists('Dockerfile')) {
      this.steps.echo "No Dockerfile found, skipping tech stack maintenance"
      return
    }
    steps.sh "grep -E '^FROM' Dockerfile | awk '{print \$2}' | awk -F ':' '{printf(\"%s\", \$2)}' > languageVersionProc"
    def result = steps.readFile('languageVersionProc')
    return result
  }

  static String getJson(applicationId, repositoryName, b64Dependencies, parser, language, languageVersion) {
    return """\
             {
             "vcsHost": "Github HMCTS",
             "hmctsApplication": "${applicationId}",
             "codeRepository": "${repositoryName}",
             "encodedDependecyList": "${b64Dependencies}",
             "parser": "${parser}",
             "language": "${language}",
             "languageVersion": "${languageVersion}"
             }
             """.stripIndent()
  }

  void updateDependencies(String dependencies, String parser) {
    String applicationId = this.getApplicationId()
    String repositoryName = this.getRepositoryName()
    String language = this.getLanguage()
    String languageVersion = this.getLanguageVersion()

    String b64Dependencies = dependencies.bytes.encodeBase64().toString()

    if (applicationId && repositoryName && b64Dependencies && parser && language && languageVersion) {

      String jsonPayload = getJson(applicationId, repositoryName, b64Dependencies, parser, language, languageVersion)

      this.steps.writeFile(file: 'payload.json', text: jsonPayload);
      // gzip the payload
      this.steps.sh "gzip payload.json"

      this.steps.sh """curl -w "%{http_code}" --location --request POST '${this.apiUrl}/api/dependencies' \
                  --header 'Authorization: Bearer ${this.apiKey}' \
                  --header 'Content-Type: application/json' \
                  --header 'content-encoding: gzip' \
                  --data-binary '@payload.json.gz'
                  """
    } else {
      this.steps.echo "Missing required parameters for tech stack maintenance"
    }
  }
}
