package uk.gov.hmcts.contino

import spock.lang.Specification

class RateLimitedGithubTest extends Specification {

  def steps
  def rateLimitedGithub

  def setup() {
    steps = Mock(JenkinsStepMock)
    steps.env >> [GIT_URL: 'https://github.com/hmcts/plum-frontend.git']
    rateLimitedGithub = new RateLimitedGithub(steps)
  }

  def "returns the credential unchanged when no pool matches"() {
    expect:
    rateLimitedGithub.getDistributedCredentialId('hmcts-jenkins-cnp') == 'hmcts-jenkins-cnp'
  }

  def "distributes primary and secondary credentials deterministically"() {
    given:
    def first = rateLimitedGithub.getDistributedCredentialId('hmcts-jenkins-a-to-c')
    steps.env >> [GIT_URL: 'https://github.com/hmcts/ccd-case-management-web.git']
    def second = rateLimitedGithub.getDistributedCredentialId('hmcts-jenkins-a-to-c')

    expect:
    first in ['hmcts-jenkins-a-to-c', 'hmcts-jenkins-a-to-c-2']
    second in ['hmcts-jenkins-a-to-c', 'hmcts-jenkins-a-to-c-2']
  }

  def "GET retries tertiary after primary and secondary are core-rate-limited"() {
    given:
    def candidates = []
    steps.httpRequest(_) >> { Map request ->
      candidates << request.authentication
      if (request.authentication != 'hmcts-jenkins-a-to-c-3') {
        return [status: 403, headers: [
          'x-ratelimit-resource': 'core',
          'x-ratelimit-remaining': '0'
        ], content: '']
      }
      [status: 200, headers: [:], content: '{}']
    }

    when:
    def response = rateLimitedGithub.getWithDistributedCredentials('hmcts-jenkins-a-to-c', [
      url: 'https://api.github.com/repos/hmcts/plum-frontend/issues/1/labels',
      validResponseCodes: '200'
    ])

    then:
    response.status == 200
    candidates.size() == 2
    candidates.unique().size() == 2
  }

  def "POST does not retry after core rate limiting"() {
    given:
    def candidates = []
    steps.httpRequest(_) >> { Map request ->
      candidates << request.authentication
      [status: 429, headers: [
        'x-ratelimit-resource': 'core',
        'x-ratelimit-remaining': '0'
      ], content: '']
    }

    when:
    rateLimitedGithub.postWithDistributedCredentials('hmcts-jenkins-a-to-c', [
      url: 'https://api.github.com/repos/hmcts/plum-frontend/releases',
      requestBody: '{}',
      validResponseCodes: '201'
    ])

    then:
    thrown(RuntimeException)
    candidates.size() == 1
  }

  def "returns caller-accepted status without retry"() {
    given:
    steps.httpRequest(_) >> [status: 404, headers: [:], content: '']

    expect:
    rateLimitedGithub.getWithDistributedCredentials('hmcts-jenkins-cnp', [
      url: 'https://api.github.com/repos/hmcts/plum-frontend/releases/latest',
      validResponseCodes: '200,404'
    ]).status == 404
  }
}
