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

  void updateDependencies(String dependencies, String parser) {
    if (!this.steps.fileExists('Dockerfile')) {
      this.steps.echo "No Dockerfile found, skipping tech stack maintenance"
      return
    }

    def applicationId = this.steps.env.ARDOQ_APPLICATION_ID
    if (!applicationId?.trim()) {
      this.steps.echo "Ardoq Application Id is not configured for ${this.steps.env.GIT_URL}"
      return
    }

    String repositoryName = new RepositoryUrl().getShortWithoutOrgOrSuffix(this.steps.env.GIT_URL)
    steps.sh "grep -E '^FROM' Dockerfile | awk '{print \$2}' | awk -F ':' '{printf(\"%s\", \$1)}' | tr '/' '\\n' | tail -1 > languageProc"
    steps.sh "grep -E '^FROM' Dockerfile | awk '{print \$2}' | awk -F ':' '{printf(\"%s\", \$2)}' > languageVersionProc"

    String language = steps.readFile('languageProc')
    String languageVersion = steps.readFile('languageVersionProc')

    String b64Dependencies = dependencies.bytes.encodeBase64().toString()

    String jsonPayload = """\
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

    this.steps.echo("JSON Payload to send: ${jsonPayload}")

    this.steps.writeFile(file: 'payload.json', text: jsonPayload);
    // gzip the payload
    this.steps.sh "gzip payload.json"

    this.steps.sh """curl -w "%{http_code}" --location --request POST '${this.apiUrl}/api/dependencies' \
                --header 'Authorization: Bearer ${this.apiKey}' \
                --header 'Content-Type: application/json' \
                --header 'content-encoding: gzip' \
                --data-binary '@payload.json.gz'
                """
  }
}
