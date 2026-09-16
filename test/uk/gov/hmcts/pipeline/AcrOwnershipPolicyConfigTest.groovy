package uk.gov.hmcts.pipeline

import spock.lang.Specification
import uk.gov.hmcts.contino.JenkinsStepMock

import static org.assertj.core.api.Assertions.assertThat

class AcrOwnershipPolicyConfigTest extends Specification {

  def steps

  def setup() {
    AcrOwnershipPolicyConfig.ownershipPolicyMap = null
    steps = Mock(JenkinsStepMock.class)
  }

  def "loads mode and product allow list from central yaml"() {
    given:
    steps.env >> [JENKINS_CONFIG_REPO: 'cnp-jenkins-config']
    steps.httpRequest(_) >> [content: '''
mode: enforce
allow_list:
  global:
    - shared/base-image
  recipes:
    - recipes/frontend
''']
    steps.readYaml(_) >> [
      mode: 'enforce',
      allow_list: [
        global: ['shared/base-image'],
        recipes: ['recipes/frontend']
      ]
    ]

    when:
    def policy = new AcrOwnershipPolicyConfig(steps).getOwnershipPolicy('recipes')

    then:
    assertThat(policy.mode).isEqualTo('enforce')
    assertThat(policy.allowList).containsExactly('shared/base-image', 'recipes/frontend')
    assertThat(policy.warningMessage).isNull()
  }

  def "applies per product mode override from products map"() {
    given:
    steps.env >> [JENKINS_CONFIG_REPO: 'cnp-jenkins-config']
    steps.httpRequest(_) >> [content: 'mode: enforce']
    steps.readYaml(_) >> [
      mode: 'enforce',
      allow_list: [
        products: [
          recipes: [
            mode: 'off',
            repositories: ['recipes/frontend']
          ]
        ]
      ]
    ]

    when:
    def policy = new AcrOwnershipPolicyConfig(steps).getOwnershipPolicy('recipes')

    then:
    assertThat(policy.mode).isEqualTo('off')
    assertThat(policy.allowList).containsExactly('recipes/frontend')
  }

  def "falls back to global mode when product override mode is invalid"() {
    given:
    steps.env >> [JENKINS_CONFIG_REPO: 'cnp-jenkins-config']
    steps.httpRequest(_) >> [content: 'mode: enforce']
    steps.readYaml(_) >> [
      mode: 'enforce',
      allow_list: [
        products: [
          recipes: [
            mode: 'invalid',
            repositories: ['recipes/frontend']
          ]
        ]
      ]
    ]

    when:
    def policy = new AcrOwnershipPolicyConfig(steps).getOwnershipPolicy('recipes')

    then:
    assertThat(policy.mode).isEqualTo('enforce')
    assertThat(policy.allowList).containsExactly('recipes/frontend')
  }

  def "normalizes PR product when resolving product allow list"() {
    given:
    steps.env >> [JENKINS_CONFIG_REPO: 'cnp-jenkins-config']
    steps.httpRequest(_) >> [content: 'mode: audit']
    steps.readYaml(_) >> [
      mode: 'audit',
      allow_list: [
        recipes: ['recipes/frontend']
      ]
    ]

    when:
    def policy = new AcrOwnershipPolicyConfig(steps).getOwnershipPolicy('pr-321-recipes')

    then:
    assertThat(policy.mode).isEqualTo('audit')
    assertThat(policy.allowList).containsExactly('recipes/frontend')
  }

  def "falls back to mode off with warning when central policy unavailable"() {
    given:
    steps.env >> [JENKINS_CONFIG_REPO: 'cnp-jenkins-config']
    steps.httpRequest(_) >> { throw new RuntimeException('404 Not Found') }

    when:
    def policy = new AcrOwnershipPolicyConfig(steps).getOwnershipPolicy('recipes')

    then:
    assertThat(policy.mode).isEqualTo('off')
    assertThat(policy.allowList).isEmpty()
    assertThat(policy.warningMessage.toString()).contains('Falling back to mode=off')
  }
}
