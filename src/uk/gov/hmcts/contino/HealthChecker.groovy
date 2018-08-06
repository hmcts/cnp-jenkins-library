package uk.gov.hmcts.contino

class HealthChecker {

  def steps

  HealthChecker(steps) {
    this.steps = steps
  }

  def check(url, sleepDuration, maxRetries) {

    int retryCounter = 0

    steps.retry(maxRetries) {
      steps.echo "Attempt number: " + (1 + retryCounter)

      def response = steps.httpRequest(
        acceptType: 'APPLICATION_JSON',
        consoleLogResponseBody: true,
        contentType: 'APPLICATION_JSON',
        timeout: 10,
        url: url,
        validResponseCodes: '200:599',
        ignoreSslErrors: true
      )

      if (response.status > 300) {
        ++retryCounter
        if (retryCounter < maxRetries) {
          steps.sleep sleepDuration
        }
        steps.echo "Service isn’t healthy, will retry up to ${maxRetries} times"
        throw new RuntimeException()
      }
    }
  }
}
