package uk.gov.hmcts.contino

import spock.lang.Specification

class DockerTest extends Specification {

  static final String IMAGE_NAME = 'cnpacr.azurecr.io/hmcts/my-app:pr-76'
  static final String REGISTRY_HOST = 'cnpacr.azurecr.io'
  static final String REGISTRY_USERNAME = 'username'
  static final String REGISTRY_PASSWORD = 'password'

  def steps
  def dockerImage
  def docker

  void setup() {
    dockerImage = Mock(DockerImage)
    steps = Mock(JenkinsStepMock)
    docker = new Docker(steps)

    dockerImage.getTaggedName() >> IMAGE_NAME
  }

  def "Login"() {
    when:
      docker.login(REGISTRY_HOST, REGISTRY_USERNAME, REGISTRY_PASSWORD)

    then:
    1 * steps.sh({it.containsKey('script') &&
                  it.get('script').contains("docker login ${REGISTRY_HOST} -u ${REGISTRY_USERNAME} -p ${REGISTRY_PASSWORD}") &&
                  it.containsKey('returnStdout') &&
                  it.get('returnStdout').equals(true)})
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
