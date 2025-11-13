package uk.gov.hmcts.contino

import spock.lang.Shared
import spock.lang.Specification

class HelmTest extends Specification {

  static final String CHART = "my-chart"
  static final String CHART_PATH = "charts/${CHART}"
  static final String SUBSCRIPTION = "sandbox"
  static final String REGISTRY_NAME = "hmctspublic"
  static final String REGISTRY_SUBSCRIPTION = "DCD-CNP-DEV"
  static final String DOCKER_HUB_USERNAME = "dockerhubuser"
  static final String DOCKER_HUB_PASSWORD = "dockerhubpass"

  @Shared
  def steps
  def helm

  def setup() {
    steps = Mock(JenkinsStepMock.class)
    steps.env >> [AKS_RESOURCE_GROUP: "cnp-aks-rg",
                  AKS_CLUSTER_NAME: "cnp-aks-cluster",
                  TEAM_NAMESPACE: "cnp",
                  SUBSCRIPTION_NAME: "${SUBSCRIPTION}",
                  ARM_SUBSCRIPTION_ID: "subscription-id-123",
                  REGISTRY_NAME: "${REGISTRY_NAME}",
                  REGISTRY_SUBSCRIPTION: "${REGISTRY_SUBSCRIPTION}",
                  DOCKER_HUB_USERNAME: "${DOCKER_HUB_USERNAME}",
                  DOCKER_HUB_PASSWORD: "${DOCKER_HUB_PASSWORD}",
                  BRANCH_NAME: "master"]
    helm = new Helm(steps, CHART)
  }

  def "constructor should initialize all properties correctly"() {
    expect:
      helm.subscription == SUBSCRIPTION
      helm.subscriptionId == "subscription-id-123"
      helm.resourceGroup == "cnp-aks-rg"
      helm.registryName == REGISTRY_NAME
      helm.chartLocation == CHART_PATH
      helm.chartName == CHART
      helm.namespace == "cnp"
      helm.dockerHubUsername == DOCKER_HUB_USERNAME
      helm.dockerHubPassword == DOCKER_HUB_PASSWORD
      helm.registrySubscription == REGISTRY_SUBSCRIPTION
  }

  def "setup() should perform all authentication and configuration steps"() {
    when:
      helm.setup()

    then:
      1 * steps.sh({it.containsKey('script') && 
        it.get('script').contains("acr login --name ${REGISTRY_NAME}")})
      1 * steps.sh({it.containsKey('script') && 
        it.get('script').contains("configure --defaults acr=${REGISTRY_NAME}")})
      1 * steps.sh({it.containsKey('script') && 
        it.get('script').contains("docker login") &&
        it.get('script').contains("${DOCKER_HUB_USERNAME}") &&
        it.get('script').contains("${DOCKER_HUB_PASSWORD}")})
      1 * steps.echo('Clear out helm repo before re-adding')
      1 * steps.sh({it.containsKey('label') && 
        it.get('label') == "helm repo rm ${REGISTRY_NAME}" &&
        it.get('script').contains('helm repo rm $REGISTRY_NAME')})
  }

  def "configureAcr() should use the subscription passed in"() {
    when:
      helm.configureAcr()

    then:
      1 * steps.sh({it.containsKey('script') &&
        it.get('script').contains("env AZURE_CONFIG_DIR=/opt/jenkins/.azure-${SUBSCRIPTION}")})
  }

  def "removeRepo() should remove helm repo with correct registry name"() {
    when:
      helm.removeRepo()

    then:
      1 * steps.echo('Clear out helm repo before re-adding')
      1 * steps.sh({it.containsKey('label') && 
        it.get('label') == "helm repo rm ${REGISTRY_NAME}" &&
        it.get('script').contains('helm repo rm $REGISTRY_NAME') &&
        it.get('script').contains('echo "Helm repo may not exist on disk, skipping remove"')})
  }

  def "authenticateAcr() should login to ACR"() {
    when:
      helm.authenticateAcr()

    then:
      1 * steps.sh({it.containsKey('script') && 
        it.get('script').contains("acr login --name ${REGISTRY_NAME}")})
  }

  def "authenticateDockerHub() should login to Docker Hub"() {
    when:
      helm.authenticateDockerHub()

    then:
      1 * steps.echo('Log into Docker Hub')
      1 * steps.sh({it.containsKey('script') && 
        it.get('script').contains("docker login") &&
        it.get('script').contains("${DOCKER_HUB_USERNAME}") &&
        it.get('script').contains("${DOCKER_HUB_PASSWORD}")})
  }

  def "dependencyUpdate() should execute with the correct chart"() {
    when:
      helm.dependencyUpdate()

    then:
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("helm dependency update ${CHART_PATH}") &&
                    it.containsKey('returnStdout') &&
                    it.get('returnStdout').equals(true)
      })
  }

  def "lint() should execute with the correct chart and values"() {
    when:
      helm.lint(["val1.yaml", "val2.yaml"])

    then:
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("helm lint ${CHART_PATH}") &&
                    it.get('script').contains("-f val1.yaml -f val2.yaml") &&
                    it.containsKey('returnStdout') &&
                    it.get('returnStdout').equals(true)
      })
  }

  def "publishIfNotExists() should publish when chart version not found in registry"() {
    given:
      def version = "1.0.0"
      steps.sh({it.containsKey('script') && 
        it.get('script').contains("helm inspect chart")}) >> version
      steps.sh({it.containsKey('script') && 
        it.get('script').contains("helm pull")}) >> { throw new Exception("Not found") }

    when:
      helm.publishIfNotExists(["values.yaml"])

    then:
      1 * steps.sh({it.containsKey('script') && 
        it.get('script').contains("configure --defaults acr=${REGISTRY_NAME}")})
      1 * steps.sh({it.containsKey('script') && 
        it.get('script').contains("acr login --name ${REGISTRY_NAME}")})
      1 * steps.sh({it.containsKey('script') && 
        it.get('script').contains("helm package ${CHART_PATH}")})
      1 * steps.sh({it.containsKey('script') && 
        it.get('script').contains("helm push ${CHART_PATH}/${CHART}-${version}.tgz oci://${REGISTRY_NAME}.azurecr.io/helm/")})
      1 * steps.sh({it.containsKey('script') && 
        it.get('script').contains("rm ${CHART_PATH}/${CHART}-${version}.tgz")})
      1 * steps.echo("Published ${CHART}-${version} to ${REGISTRY_NAME}")
  }

  def "publishIfNotExists() should skip publish when chart version already exists in registry"() {
    given:
      def version = "1.0.0"
      steps.sh({it.containsKey('script') && 
        it.get('script').contains("helm inspect chart")}) >> version
      steps.sh({it.containsKey('script') && 
        it.get('script').contains("helm pull")}) >> version

    when:
      helm.publishIfNotExists(["values.yaml"])

    then:
      0 * steps.sh({it.containsKey('script') && 
        it.get('script').contains("helm package")})
      0 * steps.sh({it.containsKey('script') && 
        it.get('script').contains("helm push")})
      1 * steps.echo({it.contains("Chart already published, skipping publish")})
  }

  def "publishToGitIfNotExists() should execute push script with correct parameters"() {
    given:
      def version = "2.0.0"
      steps.sh({it.containsKey('script') && 
        it.get('script').contains("helm inspect chart")}) >> version
      steps.libraryResource('uk/gov/hmcts/helm/push-helm-charts-to-git.sh') >> "#!/bin/bash\necho test"
      steps.usernamePassword(_ as Map) >> [credentialsId: 'git-creds', passwordVariable: 'BEARER_TOKEN', usernameVariable: 'APP_ID']

    when:
      helm.publishToGitIfNotExists(["values.yaml"])

    then:
      1 * steps.writeFile({it.file == 'push-helm-charts-to-git.sh'})
      1 * steps.withCredentials(_, _) >> { args -> args[1].call() }
      1 * steps.sh({it.containsKey('script') && 
        it.get('script').contains("chmod +x push-helm-charts-to-git.sh") &&
        it.get('script').contains("./push-helm-charts-to-git.sh ${CHART_PATH} ${CHART} ${version}")})
      1 * steps.sh('rm push-helm-charts-to-git.sh')
  }

  def "upgrade() should execute with the correct chart and values with enhanced wait on PR"() {
    given:
      steps.libraryResource('uk/gov/hmcts/helm/aks-debug-info.sh') >> "#!/bin/bash\necho debug"
      steps.env >> [AKS_RESOURCE_GROUP: "cnp-aks-rg",
                    AKS_CLUSTER_NAME: "cnp-aks-cluster",
                    TEAM_NAMESPACE: "cnp",
                    SUBSCRIPTION_NAME: "${SUBSCRIPTION}",
                    ARM_SUBSCRIPTION_ID: "subscription-id-123",
                    REGISTRY_NAME: "${REGISTRY_NAME}",
                    REGISTRY_SUBSCRIPTION: "${REGISTRY_SUBSCRIPTION}",
                    DOCKER_HUB_USERNAME: "${DOCKER_HUB_USERNAME}",
                    DOCKER_HUB_PASSWORD: "${DOCKER_HUB_PASSWORD}",
                    BRANCH_NAME: "PR-123"]

    when:
      helm.installOrUpgrade("pr-1", ["val1", "val2"], ["--namespace cnp"])

    then:
      1 * steps.writeFile({it.file == 'aks-debug-info.sh'})
      1 * steps.sh('chmod +x aks-debug-info.sh')
      1 * steps.sh({it.containsKey('label') && 
        it.get('label') == 'helm upgrade' &&
        it.get('script').contains("helm upgrade ${CHART}-pr-1  ${CHART_PATH}  -f val1 -f val2 --namespace cnp --install --timeout 1250s")
      })
      1 * steps.sh({it.containsKey('label') && 
        it.get('label') == 'wait for install' &&
        it.get('script').contains("Waiting 30s for initial pod creation...") &&
        it.get('script').contains("sleep 30") &&
        it.get('script').contains("kubectl get pods -n cnp -l app.kubernetes.io/instance=${CHART}-pr-1,'!job-name'") &&
        it.get('script').contains("ImagePullBackOff|ErrImagePull|CrashLoopBackOff|CreateContainerConfigError") &&
        it.get('script').contains("Waiting for pods to be scheduled and ready...") &&
        it.get('script').contains("kubectl wait --for=condition=ready pod") &&
        it.get('script').contains("-l app.kubernetes.io/instance=${CHART}-pr-1,'!job-name'") &&
        it.get('script').contains("--timeout=1220s") &&
        it.get('script').contains("./aks-debug-info.sh ${CHART}-pr-1 cnp")
      })
      1 * steps.sh('rm aks-debug-info.sh')
  }

  def "upgrade() should execute with the correct chart and values without enhanced wait on non-PR branch"() {
    given:
      steps.libraryResource('uk/gov/hmcts/helm/aks-debug-info.sh') >> "#!/bin/bash\necho debug"
      steps.env >> [AKS_RESOURCE_GROUP: "cnp-aks-rg",
                    AKS_CLUSTER_NAME: "cnp-aks-cluster",
                    TEAM_NAMESPACE: "cnp",
                    SUBSCRIPTION_NAME: "${SUBSCRIPTION}",
                    ARM_SUBSCRIPTION_ID: "subscription-id-123",
                    REGISTRY_NAME: "${REGISTRY_NAME}",
                    REGISTRY_SUBSCRIPTION: "${REGISTRY_SUBSCRIPTION}",
                    DOCKER_HUB_USERNAME: "${DOCKER_HUB_USERNAME}",
                    DOCKER_HUB_PASSWORD: "${DOCKER_HUB_PASSWORD}",
                    BRANCH_NAME: "master"]

    when:
      helm.installOrUpgrade("pr-2", ["val1"], ["--namespace cnp"])

    then:
      1 * steps.writeFile({it.file == 'aks-debug-info.sh'})
      1 * steps.sh('chmod +x aks-debug-info.sh')
      1 * steps.sh({it.containsKey('label') && 
        it.get('label') == 'helm upgrade' &&
        it.get('script').contains("helm upgrade ${CHART}-pr-2  ${CHART_PATH}  -f val1 --namespace cnp --install --wait --timeout 1250s") &&
        it.get('script').contains("./aks-debug-info.sh ${CHART}-pr-2 cnp")
      })
      1 * steps.sh('rm aks-debug-info.sh')
  }

  def "upgrade() should use enhanced wait on PR branch with different PR format"() {
    given:
      steps.libraryResource('uk/gov/hmcts/helm/aks-debug-info.sh') >> "#!/bin/bash\necho debug"
      steps.env >> [AKS_RESOURCE_GROUP: "cnp-aks-rg",
                    AKS_CLUSTER_NAME: "cnp-aks-cluster",
                    TEAM_NAMESPACE: "cnp",
                    SUBSCRIPTION_NAME: "${SUBSCRIPTION}",
                    ARM_SUBSCRIPTION_ID: "subscription-id-123",
                    REGISTRY_NAME: "${REGISTRY_NAME}",
                    REGISTRY_SUBSCRIPTION: "${REGISTRY_SUBSCRIPTION}",
                    DOCKER_HUB_USERNAME: "${DOCKER_HUB_USERNAME}",
                    DOCKER_HUB_PASSWORD: "${DOCKER_HUB_PASSWORD}",
                    BRANCH_NAME: "PR-456-feature"]

    when:
      helm.installOrUpgrade("test-1", ["val1"], ["--namespace cnp"])

    then:
      1 * steps.sh({it.containsKey('label') && it.get('label') == 'helm upgrade'})
      1 * steps.sh({it.containsKey('label') && it.get('label') == 'wait for install'})
  }

  def "upgrade() should not use enhanced wait on demo branch"() {
    given:
      steps.libraryResource('uk/gov/hmcts/helm/aks-debug-info.sh') >> "#!/bin/bash\necho debug"
      steps.env >> [AKS_RESOURCE_GROUP: "cnp-aks-rg",
                    AKS_CLUSTER_NAME: "cnp-aks-cluster",
                    TEAM_NAMESPACE: "cnp",
                    SUBSCRIPTION_NAME: "${SUBSCRIPTION}",
                    ARM_SUBSCRIPTION_ID: "subscription-id-123",
                    REGISTRY_NAME: "${REGISTRY_NAME}",
                    REGISTRY_SUBSCRIPTION: "${REGISTRY_SUBSCRIPTION}",
                    DOCKER_HUB_USERNAME: "${DOCKER_HUB_USERNAME}",
                    DOCKER_HUB_PASSWORD: "${DOCKER_HUB_PASSWORD}",
                    BRANCH_NAME: "demo"]

    when:
      helm.installOrUpgrade("demo-1", ["val1"], ["--namespace cnp"])

    then:
      1 * steps.sh({it.containsKey('label') && 
        it.get('label') == 'helm upgrade' &&
        it.get('script').contains("--install --wait --timeout 1250s")
      })
      0 * steps.sh({it.containsKey('label') && it.get('label') == 'wait for install'})
  }

  def "installOrUpgrade() should throw exception when no values provided"() {
    when:
      helm.installOrUpgrade("pr-1", null, ["--namespace cnp"])

    then:
      thrown(RuntimeException)
  }

  def "installOrUpgrade() should throw exception when empty values list provided"() {
    when:
      helm.installOrUpgrade("pr-1", [], ["--namespace cnp"])

    then:
      thrown(RuntimeException)
  }

  def "delete() should execute with the correct chart and options"() {
    when:
      helm.delete("pr-1", "default")

    then:
      1 * steps.sh({it.containsKey('script') &&
        it.get('script').contains("helm uninstall ${CHART}-pr-1") &&
        it.get('script').contains("--namespace default") &&
        it.containsKey('returnStdout') &&
        it.get('returnStdout').equals(true)
      })
  }

  def "exists() should return true when deployment exists"() {
    given:
      steps.sh({it.containsKey('script') && 
        it.get('script').contains("helm list")}) >> "${CHART}-pr-1\nother-release"

    when:
      def result = helm.exists("pr-1", "default")

    then:
      result == true
  }

  def "exists() should return false when deployment does not exist"() {
    given:
      steps.sh({it.containsKey('script') && 
        it.get('script').contains("helm list")}) >> "other-release\nanother-release"

    when:
      def result = helm.exists("pr-1", "default")

    then:
      result == false
  }

  def "exists() should return false when helm list returns null"() {
    given:
      steps.sh({it.containsKey('script') && 
        it.get('script').contains("helm list")}) >> null

    when:
      def result = helm.exists("pr-1", "default")

    then:
      result == false
  }

  def "history() should execute with the correct chart and options"() {
    when:
      helm.history("pr-1", "default")

    then:
      1 * steps.sh({it.containsKey('script') &&
        it.get('script').contains("helm history ${CHART}-pr-1  --namespace default -o json") &&
        it.containsKey('returnStdout') &&
        it.get('returnStdout').equals(true)
      })
  }

  def "hasAnyFailedToDeploy() should return false when release does not exist"() {
    given:
      steps.sh({it.containsKey('script') && 
        it.get('script').contains("helm list")}) >> ""

    when:
      def result = helm.hasAnyFailedToDeploy("pr-1", "default")

    then:
      1 * steps.echo("No release deployed for: pr-1 in namespace default")
      result == false
  }

  def "hasAnyFailedToDeploy() should return false when history is empty"() {
    given:
      steps.sh({it.containsKey('script') && 
        it.get('script').contains("helm list")}) >> "${CHART}-pr-1"
      steps.sh({it.containsKey('script') && 
        it.get('script').contains("helm history")}) >> ""

    when:
      def result = helm.hasAnyFailedToDeploy("pr-1", "default")

    then:
      result == false
  }

  def "hasAnyFailedToDeploy() should return true when release has failed status"() {
    given:
      def historyJson = '[{"revision":1,"status":"deployed"},{"revision":2,"status":"failed"}]'
      steps.sh({it.containsKey('script') && 
        it.get('script').contains("helm list")}) >> "${CHART}-pr-1"
      steps.sh({it.containsKey('script') && 
        it.get('script').contains("helm history")}) >> historyJson

    when:
      def result = helm.hasAnyFailedToDeploy("pr-1", "default")

    then:
      result == true
  }

  def "hasAnyFailedToDeploy() should return true when release has pending-upgrade status"() {
    given:
      def historyJson = '[{"revision":1,"status":"deployed"},{"revision":2,"status":"pending-upgrade"}]'
      steps.sh({it.containsKey('script') && 
        it.get('script').contains("helm list")}) >> "${CHART}-pr-1"
      steps.sh({it.containsKey('script') && 
        it.get('script').contains("helm history")}) >> historyJson

    when:
      def result = helm.hasAnyFailedToDeploy("pr-1", "default")

    then:
      result == true
  }

  def "hasAnyFailedToDeploy() should return true when release has pending-install status"() {
    given:
      def historyJson = '[{"revision":1,"status":"pending-install"}]'
      steps.sh({it.containsKey('script') && 
        it.get('script').contains("helm list")}) >> "${CHART}-pr-1"
      steps.sh({it.containsKey('script') && 
        it.get('script').contains("helm history")}) >> historyJson

    when:
      def result = helm.hasAnyFailedToDeploy("pr-1", "default")

    then:
      result == true
  }

  def "hasAnyFailedToDeploy() should return false when all releases are deployed"() {
    given:
      def historyJson = '[{"revision":1,"status":"deployed"},{"revision":2,"status":"superseded"},{"revision":3,"status":"deployed"}]'
      steps.sh({it.containsKey('script') && 
        it.get('script').contains("helm list")}) >> "${CHART}-pr-1"
      steps.sh({it.containsKey('script') && 
        it.get('script').contains("helm history")}) >> historyJson

    when:
      def result = helm.hasAnyFailedToDeploy("pr-1", "default")

    then:
      result == false
  }

  def "hasAnyFailedToDeploy() should return false when JSON parsing fails"() {
    given:
      steps.sh({it.containsKey('script') && 
        it.get('script').contains("helm list")}) >> "${CHART}-pr-1"
      steps.sh({it.containsKey('script') && 
        it.get('script').contains("helm history")}) >> "invalid json"

    when:
      def result = helm.hasAnyFailedToDeploy("pr-1", "default")

    then:
      1 * steps.echo({it.contains("Failed to parse helm history JSON")})
      result == false
  }

  def "hasAnyFailedToDeploy() should handle case-insensitive status checks"() {
    given:
      def historyJson = '[{"revision":1,"status":"FAILED"},{"revision":2,"status":"Pending-Upgrade"}]'
      steps.sh({it.containsKey('script') && 
        it.get('script').contains("helm list")}) >> "${CHART}-pr-1"
      steps.sh({it.containsKey('script') && 
        it.get('script').contains("helm history")}) >> historyJson

    when:
      def result = helm.hasAnyFailedToDeploy("pr-1", "default")

    then:
      result == true
  }

}
