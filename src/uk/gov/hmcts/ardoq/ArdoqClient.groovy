package uk.gov.hmcts.ardoq

class ArdoqClient {
  String apiKey
  String apiUrl
  public steps

  ArdoqClient(apiKey, apiUrl, steps) {
    this.apiKey = apiKey
    this.apiUrl = apiUrl
    this.steps = steps
  }

  void updateDependencies(String dependencies, String applicationId, String repositoryName, String parser, String language, String languageVersion) {
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
