package uk.gov.hmcts.contino

import spock.lang.Specification
import uk.gov.hmcts.contino.azure.Acr

import static org.assertj.core.api.Assertions.*

class DockerImageTest extends Specification {

  static final String PRODUCT   = 'custard'
  static final String COMPONENT = 'back-end'
  static final String TAG = 'pr-47'

  def steps
  def dockerImage
  def projectBranch
  def acr

  void setup() {
    steps = Mock(JenkinsStepMock)
    acr = Mock(Acr)
    projectBranch = Mock(ProjectBranch)
  }

  def "getTaggedName"() {
    when:
      steps.env >> [REGISTRY_HOST: "cnpacr.azure.io"]
      projectBranch.imageTag() >> TAG
      dockerImage = new DockerImage(PRODUCT, COMPONENT, steps, projectBranch)
      def buildName = dockerImage.getTaggedName()

    then:
      assertThat(buildName).isEqualTo('cnpacr.azure.io/hmcts/custard-back-end:pr-47')
  }

  def "getDigestName should have digest"() {
    when:
      steps.env >> [REGISTRY_HOST: "cnpacr.azure.io"]
      projectBranch.imageTag() >> TAG
      acr.getImageDigest('hmcts/custard-back-end:pr-47') >> 'sha256:c8aa9687b927cb65ced1aa7bd7756c2af5e84a79b54dd67cb91177d9071396aa'
      dockerImage = new DockerImage(PRODUCT, COMPONENT, steps, projectBranch)
      def buildName = dockerImage.getDigestName(this.acr)

    then:
      assertThat(buildName).isEqualTo('cnpacr.azure.io/hmcts/custard-back-end@sha256:c8aa9687b927cb65ced1aa7bd7756c2af5e84a79b54dd67cb91177d9071396aa')
  }

  def "getDigestName should throw exception if digest env variable is not set"() {
    when:
      steps.env >> [REGISTRY_HOST: "cnpacr.azure.io"]
      projectBranch.imageTag() >> TAG
      acr.getImageDigest('hmcts/custard-back-end:pr-47') >> ''
      dockerImage = new DockerImage(PRODUCT, COMPONENT, steps, projectBranch)
      dockerImage.getDigestName(this.acr)

    then:
      thrown IllegalStateException
  }

  def "getTag should return the tag"() {
    when:
      steps.env >> [REGISTRY_HOST: "cnpacr.azure.io"]
      projectBranch.imageTag() >> TAG
      dockerImage = new DockerImage(PRODUCT, COMPONENT, steps, projectBranch)
      def tag = dockerImage.getTag()

    then:
      assertThat(tag).isEqualTo(TAG)
  }

  def "getAksServiceName should return the service name"() {
    when:
      steps.env >> [REGISTRY_HOST: "cnpacr.azure.io"]
      projectBranch.imageTag() >> TAG
      dockerImage = new DockerImage(PRODUCT, COMPONENT, steps, projectBranch)
      def serviceName = dockerImage.getAksServiceName()

    then:
      assertThat(serviceName).isEqualTo('custard-back-end-pr-47')
  }

}
