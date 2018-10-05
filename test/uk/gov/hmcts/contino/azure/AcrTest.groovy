package uk.gov.hmcts.contino.azure

import spock.lang.Specification
import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.JenkinsStepMock

class AcrTest extends Specification {

  static final String SUBSCRIPTION  = 'sandbox'
  static final String REGISTRY_NAME = 'cnpacr'
  static final String REGISTRY_RESOURCE_GROUP = 'cnp-acr-rg'
  static final String IMAGE_NAME    = 'hmcts/alpine:sometag'

  def steps
  def acr
  def dockerImage

  def setup() {
    steps = Mock(JenkinsStepMock.class)
    dockerImage = Mock(DockerImage.class)
    acr = new Acr(steps, SUBSCRIPTION, REGISTRY_NAME, REGISTRY_RESOURCE_GROUP)
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

  def "build() should call az with acr build and correct arguments"() {
    when:
      dockerImage.getShortName() >> IMAGE_NAME
      acr.build(dockerImage)

    then:
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("az acr build --no-format -r ${REGISTRY_NAME} -t ${IMAGE_NAME} -g ${REGISTRY_RESOURCE_GROUP} .") &&
                    it.containsKey('returnStdout') &&
                    it.get('returnStdout').equals(true)})
  }

  def "getHostname() should call az with correct arguments"() {
    when:
      acr.getHostname()

    then:
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("az acr show -n ${REGISTRY_NAME} --query loginServer -otsv") &&
                    it.containsKey('returnStdout') &&
                    it.get('returnStdout').equals(true)})
  }
}
