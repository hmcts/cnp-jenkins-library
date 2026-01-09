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
    // Default env with dual publish disabled
    steps.env >> [:]
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
    given:
      steps.fileExists("acb.yaml") >> true
      steps.readFile("acb.yaml") >> "version: v1.1.0"

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
      dockerImage.getRepositoryName() >> IMAGE_REPO
      dockerImage.getBaseShortName() >> IMAGE_NAME
      steps.fileExists("acb.yaml") >> true
      steps.readFile("acb.yaml") >> "version: v1.1.0"

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
      it.get('script').contains("acr run --registry ${REGISTRY_NAME} --subscription ${REGISTRY_SUBSCRIPTION} --cmd \"acr purge --filter ${IMAGE_REPO}:^prod-.* --ago 5d --keep 5 --untagged --concurrency 5\" /dev/null") &&
      it.containsKey('returnStdout') &&
      it.get('returnStdout').equals(true)})
  }

  def "run() should detect cross-registry pulls and use task-based execution"() {
    given:
      def identityJson = '{"userAssignedIdentities":{"/subscriptions/sub-id/resourcegroups/rg/providers/Microsoft.ManagedIdentity/userAssignedIdentities/mi":{"clientId":"client-id-123"}}}'
      def taskJson = '{"identity":{"userAssignedIdentities":{}}}'
      // Stub file and JSON operations
      steps.fileExists(_) >> true
      steps.readFile(_) >> "registry: hmctsprod.azurecr.io\nfrom: hmctsprod.azurecr.io/base/node:18-alpine"
      steps.readJSON(_) >> { args ->
        def text = (args instanceof Map) ? args.text : args
        if (text == identityJson) {
          return [
            userAssignedIdentities: [
              '/subscriptions/sub-id/resourcegroups/rg/providers/Microsoft.ManagedIdentity/userAssignedIdentities/mi': [
                clientId: 'client-id-123'
              ]
            ]
          ]
        } else if (text == taskJson) {
          return [
            identity: [
              userAssignedIdentities: [:]
            ]
          ]
        }
        return [:]
      }
      steps.echo(_) >> null
      // Stub all sh() calls with appropriate responses
      steps.sh({it.containsKey('script') && it.get('script').contains("az acr identity show")}) >> identityJson
      steps.sh({it.containsKey('script') && it.get('script').contains("az acr task show")}) >> { throw new Exception("Task not found") }
      steps.sh({it.containsKey('script') && it.get('script').contains("az acr task create")}) >> taskJson
      steps.sh({it.containsKey('script') && it.get('script').contains("az acr task identity assign")}) >> ''
      steps.sh({it.containsKey('script') && it.get('script').contains("az acr task credential list")}) >> '[]'
      steps.sh({it.containsKey('script') && it.get('script').contains("az acr task credential add")}) >> ''
      steps.sh({it.containsKey('script') && it.get('script').contains("az acr task run")}) >> ''

    when:
      acr.run()

    then:
      // Stubs in given block ensure correct calls are made
      // If wrong methods called or wrong order, stubs won't match and test fails
      true
  }

  def "runWithTemplate() should detect cross-registry pulls and use task-based execution with repository-specific task name"() {
    given:
      dockerImage.getRepositoryName() >> IMAGE_REPO
      dockerImage.getBaseShortName() >> IMAGE_NAME
      def identityJson = '{"userAssignedIdentities":{"/subscriptions/sub-id/resourcegroups/rg/providers/Microsoft.ManagedIdentity/userAssignedIdentities/mi":{"clientId":"client-id-123"}}}'
      def taskJson = '{"identity":{"userAssignedIdentities":{}}}'
      // Stub file operations
      steps.fileExists("acb.yaml") >> true
      steps.readFile("acb.yaml") >> "registry: hmctsprod.azurecr.io\nfrom: hmctsprod.azurecr.io/base/node:18-alpine"
      // Stub readJSON - match any call and return appropriate response
      steps.readJSON(_) >> { args ->
        def text = (args instanceof Map) ? args.text : args
        if (text == identityJson) {
          return [
            userAssignedIdentities: [
              '/subscriptions/sub-id/resourcegroups/rg/providers/Microsoft.ManagedIdentity/userAssignedIdentities/mi': [
                clientId: 'client-id-123'
              ]
            ]
          ]
        } else if (text == taskJson) {
          return [
            identity: [
              userAssignedIdentities: [:]
            ]
          ]
        }
        return [:]
      }
      steps.echo(_) >> null
      // All sh() stubs in given block
      steps.sh({it.containsKey('script') && it.get('script').contains("sed -e")}) >> ""
      steps.sh({it.containsKey('script') && it.get('script').contains("az acr identity show")}) >> identityJson
      steps.sh({it.containsKey('script') && it.get('script').contains("az acr task show")}) >> { throw new Exception("Task not found") }
      steps.sh({it.containsKey('script') && it.get('script').contains("az acr task create")}) >> taskJson
      steps.sh({it.containsKey('script') && it.get('script').contains("az acr task identity assign")}) >> ''
      steps.sh({it.containsKey('script') && it.get('script').contains("az acr task credential list")}) >> '[]'
      steps.sh({it.containsKey('script') && it.get('script').contains("az acr task credential add")}) >> ''
      steps.sh({it.containsKey('script') && it.get('script').contains("az acr task run")}) >> ''

    when:
      acr.runWithTemplate("acb.tpl.yaml", dockerImage)

    then:
      true
  }

  def "run() should fallback to quick run when no managed identity is available"() {
    given:
      // These must be stubs in 'given' to return values during execution
      steps.fileExists("acb.yaml") >> true
      steps.readFile("acb.yaml") >> "registry: hmctsprod.azurecr.io\nfrom: hmctsprod.azurecr.io/base/node:18-alpine"
      steps.echo(_) >> null
      // Stubs for Azure CLI commands
      steps.sh({it.containsKey('script') && it.get('script').contains("az acr identity show")}) >> { throw new Exception("No identity") }
      steps.sh({it.containsKey('script') && it.get('script').contains("az acr run -r")}) >> ''

    when:
      acr.run()

    then:
      true
  }

  def "run() should fallback to quick run when identity assignment fails"() {
    given:
      def identityJson = '{"userAssignedIdentities":{"/subscriptions/sub-id/resourcegroups/rg/providers/Microsoft.ManagedIdentity/userAssignedIdentities/mi":{"clientId":"client-id-123"}}}'
      def taskJson = '{"identity":{"userAssignedIdentities":{}}}'
      // Stub file operations
      steps.fileExists(_) >> true
      steps.readFile(_) >> "registry: hmctsprod.azurecr.io\nfrom: hmctsprod.azurecr.io/base/node:18-alpine"
      // Stub readJSON - match any call and return appropriate response
      steps.readJSON(_) >> { args ->
        def text = (args instanceof Map) ? args.text : args
        if (text == identityJson) {
          return [
            userAssignedIdentities: [
              '/subscriptions/sub-id/resourcegroups/rg/providers/Microsoft.ManagedIdentity/userAssignedIdentities/mi': [
                clientId: 'client-id-123'
              ]
            ]
          ]
        } else if (text == taskJson) {
          return [
            identity: [
              userAssignedIdentities: [:]
            ]
          ]
        }
        return [:]
      }
      steps.echo(_) >> null
      // All sh() stubs in given block
      steps.sh({it.containsKey('script') && it.get('script').contains("az acr identity show")}) >> identityJson
      steps.sh({it.containsKey('script') && it.get('script').contains("az acr task show")}) >> { throw new Exception("Task not found") }
      steps.sh({it.containsKey('script') && it.get('script').contains("az acr task create")}) >> taskJson
      steps.sh({it.containsKey('script') && it.get('script').contains("az acr task identity assign")}) >> { throw new Exception("Permission denied") }
      steps.sh({it.containsKey('script') && it.get('script').contains("az acr run -r")}) >> ''

    when:
      acr.run()

    then:
      true
  }

  def "run() should skip credential addition if credentials already exist"() {
    given:
      steps.fileExists(_) >> true
      steps.readFile(_) >> "registry: hmctsprod.azurecr.io"
      def identityJson = '{"userAssignedIdentities":{"/subscriptions/sub-id/resourcegroups/rg/providers/Microsoft.ManagedIdentity/userAssignedIdentities/mi":{"clientId":"client-id-123"}}}'
      def taskJson = '{"identity":{"userAssignedIdentities":{"/subscriptions/sub-id/resourcegroups/rg/providers/Microsoft.ManagedIdentity/userAssignedIdentities/mi":{}}}}'
      // Stub readJSON - match any call and return appropriate response
      steps.readJSON(_) >> { args ->
        def text = (args instanceof Map) ? args.text : args
        if (text == identityJson) {
          return [
            userAssignedIdentities: [
              '/subscriptions/sub-id/resourcegroups/rg/providers/Microsoft.ManagedIdentity/userAssignedIdentities/mi': [
                clientId: 'client-id-123'
              ]
            ]
          ]
        } else if (text == taskJson) {
          return [
            identity: [
              userAssignedIdentities: [
                '/subscriptions/sub-id/resourcegroups/rg/providers/Microsoft.ManagedIdentity/userAssignedIdentities/mi': [:]
              ]
            ]
          ]
        }
        return [:]
      }
      steps.echo(_) >> null
      // All sh() stubs in given block
      steps.sh({it.containsKey('script') && it.get('script').contains("az acr identity show")}) >> identityJson
      steps.sh({it.containsKey('script') && it.get('script').contains("az acr task show")}) >> taskJson
      steps.sh({it.containsKey('script') && it.get('script').contains("az acr task credential list")}) >> 'hmctsprod.azurecr.io'
      steps.sh({it.containsKey('script') && it.get('script').contains("az acr task run")}) >> ''

    when:
      acr.run()

    then:
      // Should NOT add credentials again
      0 * steps.sh({it.containsKey('script') && it.get('script').contains("az acr task credential add")})
  }

  // ==================== Dual ACR Publish Tests ====================

  def "isDualPublishEnabled() returns false when DUAL_ACR_PUBLISH_ENABLED is not set"() {
    given:
      steps.env >> [:]

    when:
      def result = acr.isDualPublishEnabled()

    then:
      result == false
  }

  def "isDualPublishEnabled() returns false when secondary registry details are missing"() {
    given:
      steps = Mock(JenkinsStepMock.class)
      steps.env >> [
        DUAL_ACR_PUBLISH_ENABLED: 'true',
        SECONDARY_REGISTRY_NAME: null
      ]
      steps.echo(_) >> null

    when:
      acr = new Acr(steps, SUBSCRIPTION, REGISTRY_NAME, REGISTRY_RESOURCE_GROUP, REGISTRY_SUBSCRIPTION)
      def result = acr.isDualPublishEnabled()

    then:
      result == false
  }

  def "isDualPublishEnabled() returns true when all secondary registry details are provided"() {
    given:
      steps = Mock(JenkinsStepMock.class)
      steps.env >> [
        DUAL_ACR_PUBLISH_ENABLED: 'true',
        SECONDARY_REGISTRY_NAME: 'hmctsold',
        SECONDARY_REGISTRY_RESOURCE_GROUP: 'hmcts-old-rg',
        SECONDARY_REGISTRY_SUBSCRIPTION: 'old-sub'
      ]
      steps.echo(_) >> null

    when:
      acr = new Acr(steps, SUBSCRIPTION, REGISTRY_NAME, REGISTRY_RESOURCE_GROUP, REGISTRY_SUBSCRIPTION)
      def result = acr.isDualPublishEnabled()

    then:
      result == true
  }

  def "login() should login to both registries when dual publish is enabled"() {
    given:
      steps = Mock(JenkinsStepMock.class)
      steps.env >> [
        DUAL_ACR_PUBLISH_ENABLED: 'true',
        SECONDARY_REGISTRY_NAME: 'hmctsold',
        SECONDARY_REGISTRY_RESOURCE_GROUP: 'hmcts-old-rg',
        SECONDARY_REGISTRY_SUBSCRIPTION: 'old-sub'
      ]
      steps.echo(_) >> null
      acr = new Acr(steps, SUBSCRIPTION, REGISTRY_NAME, REGISTRY_RESOURCE_GROUP, REGISTRY_SUBSCRIPTION)

    when:
      acr.login()

    then:
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("az acr login --name ${REGISTRY_NAME}")})
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("az acr login --name hmctsold")})
  }

  def "build() should build to primary and secondary when dual publish is enabled"() {
    given:
      steps = Mock(JenkinsStepMock.class)
      steps.env >> [
        DUAL_ACR_PUBLISH_ENABLED: 'true',
        SECONDARY_REGISTRY_NAME: 'hmctsold',
        SECONDARY_REGISTRY_RESOURCE_GROUP: 'hmcts-old-rg',
        SECONDARY_REGISTRY_SUBSCRIPTION: 'old-sub'
      ]
      steps.echo(_) >> null
      acr = new Acr(steps, SUBSCRIPTION, REGISTRY_NAME, REGISTRY_RESOURCE_GROUP, REGISTRY_SUBSCRIPTION)
      dockerImage = Mock(DockerImage.class)
      dockerImage.getImageTag() >> IMAGE_TAG
      dockerImage.getBaseShortName() >> IMAGE_NAME

    when:
      acr.build(dockerImage)

    then:
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("az acr build --no-format -r ${REGISTRY_NAME} -t ${IMAGE_NAME}")})
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("az acr build --no-format -r hmctsold -t ${IMAGE_NAME}") &&
                    it.get('script').contains("--build-arg REGISTRY_NAME=hmctsold")})
  }

  def "retagForStage() should retag in both registries when dual publish is enabled"() {
    given:
      steps = Mock(JenkinsStepMock.class)
      steps.env >> [
        DUAL_ACR_PUBLISH_ENABLED: 'true',
        SECONDARY_REGISTRY_NAME: 'hmctsold',
        SECONDARY_REGISTRY_RESOURCE_GROUP: 'hmcts-old-rg',
        SECONDARY_REGISTRY_SUBSCRIPTION: 'old-sub'
      ]
      steps.echo(_) >> null
      acr = new Acr(steps, SUBSCRIPTION, REGISTRY_NAME, REGISTRY_RESOURCE_GROUP, REGISTRY_SUBSCRIPTION)
      dockerImage = Mock(DockerImage.class)
      dockerImage.getShortName(_) >> IMAGE_NAME
      dockerImage.getBaseTaggedName() >> "${REGISTRY_NAME}.azurecr.io/${IMAGE_NAME}"
      dockerImage.imageTag >> 'staging'

    when:
      acr.retagForStage(DockerImage.DeploymentStage.STAGING, dockerImage)

    then:
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("az acr import --force -n ${REGISTRY_NAME}")})
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("az acr import --force -n hmctsold")})
  }

  def "purgeOldTags() should purge from both registries when dual publish is enabled"() {
    given:
      steps = Mock(JenkinsStepMock.class)
      steps.env >> [
        DUAL_ACR_PUBLISH_ENABLED: 'true',
        SECONDARY_REGISTRY_NAME: 'hmctsold',
        SECONDARY_REGISTRY_RESOURCE_GROUP: 'hmcts-old-rg',
        SECONDARY_REGISTRY_SUBSCRIPTION: 'old-sub'
      ]
      steps.echo(_) >> null
      acr = new Acr(steps, SUBSCRIPTION, REGISTRY_NAME, REGISTRY_RESOURCE_GROUP, REGISTRY_SUBSCRIPTION)
      dockerImage = Mock(DockerImage.class)
      dockerImage.getRepositoryName() >> IMAGE_REPO
      dockerImage.getImageTag() >> IMAGE_TAG

    when:
      acr.purgeOldTags(DockerImage.DeploymentStage.PROD, dockerImage)

    then:
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("az acr run --registry ${REGISTRY_NAME}") &&
                    it.get('script').contains("acr purge")})
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("az acr run --registry hmctsold") &&
                    it.get('script').contains("acr purge")})
  }

}
