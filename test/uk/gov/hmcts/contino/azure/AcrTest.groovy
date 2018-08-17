package uk.gov.hmcts.contino.azure

import spock.lang.Specification
import uk.gov.hmcts.contino.JenkinsStepMock

class AcrTest extends Specification {

  static final String SUBSCRIPTION  = 'sandbox'
  static final String REGISTRY_NAME = 'cnpacr'
  static final String IMAGE_NAME    = 'hmcts/alpine:sometag'

  def steps
  def acr

  def setup() {
    steps = Mock(JenkinsStepMock.class)
    acr = new Acr(steps, SUBSCRIPTION, REGISTRY_NAME)
  }

  def "login() should login with registry name"() {
    when:
      acr.login()

    then:
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("az acr login --name ${REGISTRY_NAME}") &&
                    it.containsKey('returnStdout') &&
                    it.get('returnStdout').equals(true)})
  }

  def "getImageDigest() should call az with registry and image name"() {
    when:
      acr.getImageDigest(IMAGE_NAME)

    then:
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("az acr repository show --name ${REGISTRY_NAME} --image ${IMAGE_NAME} --query [digest] -otsv") &&
                    it.containsKey('returnStdout') &&
                    it.get('returnStdout').equals(true)})
  }
}
