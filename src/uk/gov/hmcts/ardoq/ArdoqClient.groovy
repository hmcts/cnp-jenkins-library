package uk.gov.hmcts.ardoq

import java.util.zip.GZIPOutputStream

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

//     curl -w "%{http_code}" --location --request POST '${{ secrets.ARDOQ_ADAPTER_URL }}?async=true' \
//                --header 'Authorization: Bearer ${{ secrets.ARDOQ_ADAPTER_KEY }}' \
//                --header 'Content-Type: application/json' \
//                --header 'content-encoding: gzip' \
//                --data-binary '@payload.json.gz'

    def response = this.steps.httpRequest(httpMode: 'POST',
      customHeaders:[
        [name:'Authorization', value:"Bearer " + this.apiKey],
        [name:'content-encoding', value:'gzip']
      ],
      contentType: 'APPLICATION_JSON',
      uploadFile: 'payload.json.gz',
      url: this.apiUrl + "/api/dependencies",
      consoleLogResponseBody: true,
      validResponseCodes: '202')

    if (response.status == 200) {
      this.steps.echo "Dependencies sent to API successfully"
    } else {
      this.steps.echo "Error sending dependencies to API. Status code: ${response.status}\n${response.content}"
    }
  }

  private static def zip(String s) {
    def targetStream = new ByteArrayOutputStream()
    def zipStream = new GZIPOutputStream(targetStream)
    zipStream.write(s.getBytes('UTF-8'))
    zipStream.close()
    def zippedBytes = targetStream.toByteArray()
    targetStream.close()
    return zippedBytes
  }
}
