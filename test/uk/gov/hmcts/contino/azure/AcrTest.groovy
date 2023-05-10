package uk.gov.hmcts.contino.azure

import spock.lang.Specification
import uk.gov.hmcts.contino.DockerImage
import uk.gov.hmcts.contino.JenkinsStepMock

class AcrTest extends Specification {

  static final String SUBSCRIPTION  = 'sandbox'
  static final String REGISTRY_NAME = 'cnpacr'
  static final String REGISTRY_RESOURCE_GROUP = 'cnp-acr-rg'
  static final String REGISTRY_SUBSCRIPTION = 'a-sub'
  static final String IMAGE_NAME = 'hmcts/alpine:sometag'
  static final String IMAGE_REPO = 'hmcts/alpine'
  static final String IMAGE_TAG = "sometag"

  def steps
  def acr
  def dockerImage

  def setup() {
    steps = Mock(JenkinsStepMock.class)
    dockerImage = Mock(DockerImage.class)
    acr = new Acr(steps, SUBSCRIPTION, REGISTRY_NAME, REGISTRY_RESOURCE_GROUP, REGISTRY_SUBSCRIPTION)
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
                    it.get('script').contains("az acr repository show --name ${REGISTRY_NAME} --image ${IMAGE_NAME} --subscription ${REGISTRY_SUBSCRIPTION} --query [digest] -o tsv") &&
                    it.containsKey('returnStdout') &&
                    it.get('returnStdout').equals(true)})
  }

  def "build() should call az with acr build and correct arguments"() {
    when:
      dockerImage.getImageTag() >> IMAGE_TAG
      dockerImage.getBaseShortName() >> IMAGE_NAME
      acr.build(dockerImage)

    then:
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("az acr build --no-format -r ${REGISTRY_NAME} -t ${IMAGE_NAME} --subscription ${REGISTRY_SUBSCRIPTION} -g ${REGISTRY_RESOURCE_GROUP} --build-arg REGISTRY_NAME=${REGISTRY_NAME} .") &&
                    it.containsKey('returnStdout') &&
                    it.get('returnStdout').equals(true)})
  }

  def "build() should call az with acr build and correct additional arguments"() {
    when:
    dockerImage.getImageTag() >> IMAGE_TAG
    dockerImage.getBaseShortName() >> IMAGE_NAME
    acr.build(dockerImage, " --build-arg DEV_MODE=true")

    then:
    1 * steps.sh({it.containsKey('script') &&
      it.get('script').contains("az acr build --no-format -r ${REGISTRY_NAME} -t ${IMAGE_NAME} --subscription ${REGISTRY_SUBSCRIPTION} -g ${REGISTRY_RESOURCE_GROUP} --build-arg REGISTRY_NAME=${REGISTRY_NAME} --build-arg DEV_MODE=true .") &&
      it.containsKey('returnStdout') &&
      it.get('returnStdout').equals(true)})
  }

  def "getHostname() should call az with correct arguments"() {
    when:
      acr.getHostname()

    then:
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("az acr show -n ${REGISTRY_NAME} --subscription ${REGISTRY_SUBSCRIPTION} --query loginServer -otsv") &&
                    it.containsKey('returnStdout') &&
                    it.get('returnStdout').equals(true)})
  }

  def "run() should call the run command with the right registry group and resource group"() {
    when:
      acr.run()

    then:
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("acr run -r ${REGISTRY_NAME} -g ${REGISTRY_RESOURCE_GROUP} --subscription ${REGISTRY_SUBSCRIPTION} .") &&
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
      1 * steps.sh({it.get('script').contains("acr run -r ${REGISTRY_NAME} -g ${REGISTRY_RESOURCE_GROUP} --subscription ${REGISTRY_SUBSCRIPTION} .") &&
                    it.get('returnStdout').equals(true)
                  })

  }

  def "retagForStage() should call the import command with the provided arguments"() {
    when:
      dockerImage.getImageTag() >> IMAGE_TAG
      dockerImage.getShortName() >> IMAGE_NAME
      dockerImage.getShortName(DockerImage.DeploymentStage.PROD) >> "${IMAGE_NAME}-prod"
      dockerImage.getTaggedName() >> "${REGISTRY_NAME}.azurecr.io/${IMAGE_NAME}"
      acr.retagForStage(DockerImage.DeploymentStage.PROD, dockerImage)

    then:
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("acr import --force -n ${REGISTRY_NAME} -g ${REGISTRY_RESOURCE_GROUP} --subscription ${REGISTRY_SUBSCRIPTION} --source ${REGISTRY_NAME}.azurecr.io/${IMAGE_NAME} -t ${IMAGE_NAME}-prod") &&
                    it.containsKey('returnStdout') &&
                    it.get('returnStdout').equals(true)})
  }

  def "hasTag should return false in case of error"() {
    when:
      dockerImage.getTag() >> IMAGE_TAG
      dockerImage.getRepositoryName() >> IMAGE_REPO
      acr.steps.sh(_) >> ''
      acr.steps.error(_) >> { throw new Exception(_ as String) }

      def hasTag = acr.hasTag(dockerImage)

    then:
      assert hasTag == false
  }

  def "purgeOldTags() should call the purge command with the provided arguments"() {
    when:
    dockerImage.getImageTag() >> IMAGE_TAG
    dockerImage.getShortName() >> IMAGE_NAME
    dockerImage.getRepositoryName() >> IMAGE_REPO
    acr.purgeOldTags(DockerImage.DeploymentStage.PROD, dockerImage)

    then:
    1 * steps.sh({it.containsKey('script') &&
      it.get('script').contains("acr run --registry ${REGISTRY_NAME} --subscription ${REGISTRY_SUBSCRIPTION} --cmd \"acr purge --filter ${IMAGE_REPO}:^prod-.* --ago 5d --keep 6 --untagged --concurrency 5\" /dev/null") &&
      it.containsKey('returnStdout') &&
      it.get('returnStdout').equals(true)})
  }

}
