package uk.gov.hmcts.contino

import spock.lang.Specification

class GithubRateLimitRotatorTest extends Specification {

  def steps
  def rotator

  def setup() {
    steps = Mock(JenkinsStepMock)
    steps.usernamePassword(_) >> { Map params -> params }
    steps.withCredentials(_, _) >> { List credentials, Closure body -> body.call() }
    rotator = new GithubRateLimitRotator(steps)
  }

  def "matches a credential to the correct pool"() {
    expect:
    rotator.resolvePool('hmcts-jenkins-a-to-c') == [
      'hmcts-jenkins-a-to-c',
      'hmcts-jenkins-a-to-c-2',
      'hmcts-jenkins-a-to-c-3'
    ]
  }

  def "returns the credential unchanged when no pool matches"() {
    given:
    steps.httpRequest(_) >> { Map request ->
      assert request.authentication == 'hmcts-jenkins-other'
      [status: 200, headers: [:], content: '{}']
    }

    when:
    def response = rotator.httpRequest('hmcts-jenkins-other', [
      httpMode: 'GET',
      url: 'https://api.github.com/rate_limit',
      validResponseCodes: '200'
    ])

    then:
    response.status == 200
  }

  def "rotates to the secondary credential when core is exhausted"() {
    given:
    int requestNumber = 0
    steps.httpRequest(_) >> { Map request ->
      requestNumber++
      if (requestNumber == 1) {
        assert request.authentication == 'hmcts-jenkins-a-to-c'
        return [status: 403, headers: [
          'x-ratelimit-resource': 'core',
          'x-ratelimit-remaining': '0'
        ], content: '']
      }
      assert request.authentication == 'hmcts-jenkins-a-to-c-2'
      return [status: 200, headers: [:], content: '{}']
    }

    when:
    def response = rotator.httpRequest('hmcts-jenkins-a-to-c', [
      httpMode: 'GET',
      url: 'https://api.github.com/repos/hmcts/example/issues/1/labels',
      validResponseCodes: '200'
    ])

    then:
    response.status == 200
  }

  def "rotates to the tertiary credential after secondary is exhausted"() {
    given:
    int requestNumber = 0
    steps.httpRequest(_) >> { Map request ->
      requestNumber++
      if (requestNumber == 1) {
        assert request.authentication == 'hmcts-jenkins-a-to-c'
      } else if (requestNumber == 2) {
        assert request.authentication == 'hmcts-jenkins-a-to-c-2'
      } else {
        assert request.authentication == 'hmcts-jenkins-a-to-c-3'
        return [status: 200, headers: [:], content: '{}']
      }
      [status: 403, headers: [
        'x-ratelimit-resource': 'core',
        'x-ratelimit-remaining': '0'
      ], content: '']
    }

    when:
    def response = rotator.httpRequest('hmcts-jenkins-a-to-c', [
      httpMode: 'GET',
      url: 'https://api.github.com/repos/hmcts/example/issues/1/labels',
      validResponseCodes: '200'
    ])

    then:
    response.status == 200
  }

  def "does not rotate for a different 403"() {
    given:
    steps.httpRequest(_) >> [status: 403, headers: [
      'x-ratelimit-resource': 'core',
      'x-ratelimit-remaining': '9'
    ], content: '']

    when:
    rotator.httpRequest('hmcts-jenkins-a-to-c', [
      httpMode: 'GET',
      url: 'https://api.github.com/repos/hmcts/example/issues/1/labels',
      validResponseCodes: '200'
    ])

    then:
    thrown(RuntimeException)
  }

  def "throws when a pooled credential cannot be bound"() {
    given:
    steps.usernamePassword(_) >> { Map params ->
      if (params.credentialsId == 'hmcts-jenkins-d-to-i-2') {
        throw new RuntimeException('missing credential')
      }
      params
    }
    rotator = new GithubRateLimitRotator(steps)

    when:
    rotator.httpRequest('hmcts-jenkins-d-to-i', [
      httpMode: 'GET',
      url: 'https://api.github.com/rate_limit',
      validResponseCodes: '200'
    ])

    then:
    def err = thrown(IllegalStateException)
    err.message.contains('hmcts-jenkins-d-to-i-2')
  }

  def "does not rotate mutating requests"() {
    given:
    steps.httpRequest(_) >> [status: 429, headers: [
      'x-ratelimit-resource': 'core',
      'x-ratelimit-remaining': '0'
    ], content: '']

    when:
    rotator.httpRequest('hmcts-jenkins-a-to-c', [
      httpMode: 'POST',
      url: 'https://api.github.com/repos/hmcts/example/releases',
      validResponseCodes: '201'
    ])

    then:
    thrown(RuntimeException)
  }
}
