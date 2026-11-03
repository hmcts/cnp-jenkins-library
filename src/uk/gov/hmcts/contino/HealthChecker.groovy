package uk.gov.hmcts.contino

class HealthChecker {

  def steps

  HealthChecker(steps) {
    this.steps = steps
  }

  def check(url, sleepDuration, maxAttempts,
            Closure isHealthy = { resp -> resp.status <= 300 }) {

    int attemptCounter = 1

    steps.retry(maxAttempts) {
      steps.echo "Startup checker, attempt: " + attemptCounter + " (check app logs if this fails)"

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

        if (!isHealthy(response)) {
          steps.error "Service isn’t healthy, will try up to ${maxAttempts} times"
        }
      }
      catch (InterruptedException e) {
        throw e
      }
      catch (e) {
        // Also wait after a failed request, e.g. while a newly registered DNS record propagates
        if (attemptCounter < maxAttempts) {
          steps.sleep sleepDuration
        }
        throw e
      }
      finally {
        ++attemptCounter
      }
    }
  }
}
