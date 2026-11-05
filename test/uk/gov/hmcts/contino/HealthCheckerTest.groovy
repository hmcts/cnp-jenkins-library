package uk.gov.hmcts.contino

import spock.lang.Specification

class HealthCheckerTest extends Specification {

  static class FakeSteps {
    List responses
    List<Integer> sleeps = []
    int requests = 0

    def retry(int times, Closure body) {
      for (int i = 1; ; i++) {
        try {
          return body()
        } catch (e) {
          if (i == times) {
            throw e
          }
        }
      }
    }

    def echo(String message) {}

    def sleep(int seconds) {
      sleeps << seconds
    }

    def error(String message) {
      throw new RuntimeException(message)
    }

    def httpRequest(Map params) {
      def next = responses[requests++]
      if (next instanceof Exception) {
        throw next
      }
      return [status: next]
    }
  }

  def "waits between attempts when the request fails"() {
    given:
      def steps = new FakeSteps(responses: [new UnknownHostException('pr-1'), new UnknownHostException('pr-1'), 200])

    when:
      new HealthChecker(steps).check('https://pr-1/health', 10, 5)

    then:
      steps.requests == 3
      steps.sleeps == [10, 10]
  }

  def "waits between attempts when the service is unhealthy"() {
    given:
      def steps = new FakeSteps(responses: [503, 200])

    when:
      new HealthChecker(steps).check('https://pr-1/health', 10, 5)

    then:
      steps.requests == 2
      steps.sleeps == [10]
  }

  def "fails after the last attempt without waiting again"() {
    given:
      def steps = new FakeSteps(responses: [new UnknownHostException('pr-1')] * 3)

    when:
      new HealthChecker(steps).check('https://pr-1/health', 10, 3)

    then:
      thrown(UnknownHostException)
      steps.requests == 3
      steps.sleeps == [10, 10]
  }
}
