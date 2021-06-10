package uk.gov.hmcts.contino

import spock.lang.Specification
import uk.gov.hmcts.contino.azure.Acr

import static org.assertj.core.api.Assertions.*

class DockerImageTest extends Specification {

  static final String PRODUCT   = 'custard'
  static final String COMPONENT = 'back-end'
  static final String TAG       = 'pr-47'
  static final String REGISTRY_HOST = 'cnpacr.azure.io'
  static final String COMMIT = '379c53a716b92cf79439db07edac01ba1028535d'
  static final String LAST_COMMIT_TIMESTAMP = '202106011332'

  def dockerImage
  def projectBranch
  def acr

  void setup() {
    acr = Mock(Acr)
    projectBranch = Mock(ProjectBranch)
  }

  def "getTaggedName"() {
    when:
      acr.getHostname() >> REGISTRY_HOST
      dockerImage = new DockerImage(PRODUCT, COMPONENT, acr, TAG, COMMIT, LAST_COMMIT_TIMESTAMP)
      def buildName = dockerImage.getTaggedName()

    then:
      assertThat(buildName).isEqualTo('cnpacr.azure.io/custard/back-end:pr-47-379c53a7')
  }

  def "getDigestName should have digest"() {
    when:
      acr.getHostname() >> REGISTRY_HOST
      acr.getImageDigest('custard/back-end:pr-47-379c53a7') >> 'sha256:c8aa9687b927cb65ced1aa7bd7756c2af5e84a79b54dd67cb91177d9071396aa'
      dockerImage = new DockerImage(PRODUCT, COMPONENT, acr, TAG, COMMIT, LAST_COMMIT_TIMESTAMP)
      def buildName = dockerImage.getDigestName()

    then:
      assertThat(buildName).isEqualTo('cnpacr.azure.io/custard/back-end@sha256:c8aa9687b927cb65ced1aa7bd7756c2af5e84a79b54dd67cb91177d9071396aa')
  }

  def "getDigestName should throw exception if digest env variable is not set"() {
    when:
      acr.getHostname() >> REGISTRY_HOST
      acr.getImageDigest('hmcts/custard-back-end:pr-47') >> ''
      dockerImage = new DockerImage(PRODUCT, COMPONENT, acr, TAG, COMMIT, LAST_COMMIT_TIMESTAMP)
      dockerImage.getDigestName()

    then:
      thrown IllegalStateException
  }

  def "getImageTag should return the image tag"() {
    when:
      acr.getHostname() >> REGISTRY_HOST
      dockerImage = new DockerImage(PRODUCT, COMPONENT, acr, TAG, COMMIT, LAST_COMMIT_TIMESTAMP)
      def tag = dockerImage.getImageTag()

    then:
      assertThat(tag).isEqualTo(TAG)
  }

  def "getTag should return the tag with commit hash"() {
    when:
      acr.getHostname() >> REGISTRY_HOST
      dockerImage = new DockerImage(PRODUCT, COMPONENT, acr, TAG, COMMIT, LAST_COMMIT_TIMESTAMP)
      String tag = dockerImage.getTag()

    then:
      String expectedTag = "${TAG}-${COMMIT.substring(0,7)}-${LAST_COMMIT_TIMESTAMP}}"
      assertThat(tag).isEqualTo(expectedTag)
  }

  def "getTag for latest should return the tag without commit"() {
    when:
      acr.getHostname() >> REGISTRY_HOST
      dockerImage = new DockerImage(PRODUCT, COMPONENT, acr, 'latest', COMMIT, LAST_COMMIT_TIMESTAMP)
      def tag = dockerImage.getTag()

    then:
      assertThat(tag).isEqualTo('latest')
  }

  def "getShortName for test prod stage should return the test repo with prod + commit label"() {
    when:
    acr.getHostname() >> REGISTRY_HOST
    dockerImage = new DockerImage(PRODUCT, "${COMPONENT}-${DockerImage.TEST_REPO}", acr, 'master', COMMIT, LAST_COMMIT_TIMESTAMP)
    def name = dockerImage.getShortName(DockerImage.DeploymentStage.PROD)

    then:
    assertThat(name).isEqualTo('custard/back-end-test:prod-379c53a7')
  }

  def "getAksServiceName should return the service name"() {
    when:
      acr.getHostname() >> REGISTRY_HOST
      dockerImage = new DockerImage(PRODUCT, COMPONENT, acr, TAG, COMMIT, LAST_COMMIT_TIMESTAMP)
      def serviceName = dockerImage.getAksServiceName()

    then:
      assertThat(serviceName).isEqualTo('custard-back-end-pr-47')
  }

}
