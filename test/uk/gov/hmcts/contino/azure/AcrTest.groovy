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
      dockerImage.getImageTag() >> "sometag"
      dockerImage.getBaseShortName() >> IMAGE_NAME
      acr.build(dockerImage)

    then:
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("az acr build --no-format -r ${REGISTRY_NAME} -t ${IMAGE_NAME} -g ${REGISTRY_RESOURCE_GROUP} --build-arg REGISTRY_NAME=${REGISTRY_NAME} .") &&
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

  def "run() should call the run command with the right registry group and resource group"() {
    when:
      acr.run()

    then:
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("acr run -r ${REGISTRY_NAME} -g ${REGISTRY_RESOURCE_GROUP} .") &&
                    it.containsKey('returnStdout') &&
                    it.get('returnStdout').equals(true)})
  }

  def "runWithTemplate() should evaluate the acb.yaml file from the passed template then run the acr run command"() {
    given:
      def mockFile = Mock(File, constructorArgs :["./acb.tpl.yaml"])

    when:
      acr.runWithTemplate("acb.tpl.yaml", dockerImage)

    then:
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("sed -e \"s@{{CI_IMAGE_TAG}}@${dockerImage.getBaseShortName()}@g\" -e \"s@{{REGISTRY_NAME}}@${REGISTRY_NAME}@g\" acb.tpl.yaml > acb.yaml") &&
                    it.get('returnStdout').equals(true)
                  })
    and:
      1 * steps.sh({it.get('script').contains("acr run -r ${REGISTRY_NAME} -g ${REGISTRY_RESOURCE_GROUP} .") &&
                    it.get('returnStdout').equals(true)
                  })

  }

  def "retagForStage() should call the import command with the provided arguments"() {
    when:
      dockerImage.getImageTag() >> "sometag"
      dockerImage.getShortName() >> IMAGE_NAME
      dockerImage.getShortName(DockerImage.DeploymentStage.AAT) >> "${IMAGE_NAME}-aat"
      dockerImage.getTaggedName() >> "${REGISTRY_NAME}.azurecr.io/${IMAGE_NAME}"
      acr.retagForStage(DockerImage.DeploymentStage.AAT, dockerImage)

    then:
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("acr import --force -n ${REGISTRY_NAME} -g ${REGISTRY_RESOURCE_GROUP} --source ${REGISTRY_NAME}.azurecr.io/${IMAGE_NAME} -t ${IMAGE_NAME}-aat") &&
                    it.containsKey('returnStdout') &&
                    it.get('returnStdout').equals(true)})
  }

  def "hasTag should return false in case of error"() {
    when:
      dockerImage.getTag() >> "some_tag"
      dockerImage.getRepositoryName() >> "some_repo_name"
      acr.steps.sh(_) >> ''
      acr.steps.error(_) >> { throw new Exception(_ as String) }

      def hasTag = acr.hasTag(dockerImage)

    then:
      assert hasTag == false
  }

}
