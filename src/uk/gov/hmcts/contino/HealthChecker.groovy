package uk.gov.hmcts.contino

class HealthChecker {

  def steps

  HealthChecker(steps) {
    this.steps = steps
  }

  def check(url, sleepDuration, maxAttempts,
            Closure verifyHealthy = { resp -> resp.status <= 300 }) {

    int attemptCounter = 1

    steps.retry(maxAttempts) {
      steps.echo "Attempt number: " + attemptCounter

      try {
        def response = steps.httpRequest(
          acceptType: 'APPLICATION_JSON',
          consoleLogResponseBody: true,
          contentType: 'APPLICATION_JSON',
          timeout: 10,
          url: url,
          validResponseCodes: '200:599',
          ignoreSslErrors: true
        )

        if (!verifyHealthy(response)) {
          if (attemptCounter < maxAttempts) {
            steps.sleep sleepDuration
          }
          steps.error "Service isn’t healthy, will try up to ${maxAttempts} times"
        }
      }
      finally {
        ++attemptCounter
      }
    }
  }
}
