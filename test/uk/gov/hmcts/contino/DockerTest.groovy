package uk.gov.hmcts.contino

import spock.lang.Specification
import uk.gov.hmcts.contino.azure.Acr

class DockerTest extends Specification {

  static final String IMAGE_NAME = 'cnpacr.azrecr.io/hmcts/my-app:pr-76'

  def steps
  def acr
  def dockerImage
  def docker

  void setup() {
    acr = Mock(Acr)
    dockerImage = Mock(DockerImage)
    steps = Mock(JenkinsStepMock)
    docker = new Docker(steps, acr)

    dockerImage.getTaggedName() >> IMAGE_NAME
  }

  def "Login"() {
    when:
      docker.login()

    then:
      1 * acr.login()
  }

  def "Build"() {
    when:
      docker.build(dockerImage)

    then:
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("docker build -t ${IMAGE_NAME} .") &&
                    it.containsKey('returnStdout') &&
                    it.get('returnStdout').equals(true)})
  }

  def "Push"() {
    when:
      docker.push(dockerImage)

    then:
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("docker push ${IMAGE_NAME}") &&
                    it.containsKey('returnStdout') &&
                    it.get('returnStdout').equals(true)})
  }
}
