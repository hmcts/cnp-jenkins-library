package uk.gov.hmcts.contino

import spock.lang.Shared
import spock.lang.Specification

class HelmTest extends Specification {

  static final String CHART = "my-chart"
  static final String CHART_PATH = "charts/${CHART}"
  static final String SUBSCRIPTION = "sandbox"

  @Shared
  def steps
  def helm

  def setup() {
    steps = Mock(JenkinsStepMock.class)
    steps.env >> [AKS_RESOURCE_GROUP: "cnp-aks-rg",
                  AKS_CLUSTER_NAME: "cnp-aks-cluster",
                  TEAM_NAMESPACE: "cnp",
                  SUBSCRIPTION_NAME: "${SUBSCRIPTION}",
                  BRANCH_NAME: "PR-123"]
    helm = new Helm(steps, CHART)
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

  def "configureAcr() should use the subscription passed in"() {
    when:
    helm.configureAcr()

    then:
    1 * steps.sh({it.containsKey('script') &&
      it.get('script').contains("env AZURE_CONFIG_DIR=/opt/jenkins/.azure-${SUBSCRIPTION}")})
  }

  def "authenticateAcr() should login to the current registry using the registry subscription"() {
    given:
    def testSteps = Mock(JenkinsStepMock.class)
    testSteps.env >> [AKS_RESOURCE_GROUP: "cnp-aks-rg",
                      AKS_CLUSTER_NAME: "cnp-aks-cluster",
                      TEAM_NAMESPACE: "cnp",
                      SUBSCRIPTION_NAME: "${SUBSCRIPTION}",
                      REGISTRY_NAME: "hmctsprod",
                      REGISTRY_SUBSCRIPTION: "DCD-CNP-PROD",
                      BRANCH_NAME: "PR-123"]
    testSteps.fileExists("${CHART_PATH}/Chart.yaml") >> false
    def testHelm = new Helm(testSteps, CHART)
    testHelm.acr = Mock(uk.gov.hmcts.contino.azure.Acr)

    when:
    testHelm.authenticateAcr()

    then:
    1 * testHelm.acr.az("acr login --name hmctsprod --subscription DCD-CNP-PROD")
  }

  def "installOrUpgrade() on PR branch should execute without --wait flag and do manual wait"() {
    when:
    helm.installOrUpgrade("pr-1", ["val1", "val2"], ["--namespace cnp"])

    then:
    1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("helm dependency update ${CHART_PATH}") &&
                    it.containsKey('returnStdout') &&
                    it.get('returnStdout').equals(true)
    })
    1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("helm lint ${CHART_PATH}  -f val1 -f val2") &&
                    it.containsKey('returnStdout') &&
                    it.get('returnStdout').equals(true)
    })
    1 * steps.libraryResource('uk/gov/hmcts/helm/aks-debug-info.sh')
    1 * steps.writeFile(_)
    1 * steps.sh('chmod +x aks-debug-info.sh')
    1 * steps.sh({it.containsKey('label') && 
      it.get('label') == 'helm upgrade' &&
      it.get('script').contains("helm upgrade ${CHART}-pr-1  ${CHART_PATH}  -f val1 -f val2 --namespace cnp --install --timeout 1250s") &&
      !it.get('script').contains("--wait")
    })
    1 * steps.sh({it.containsKey('label') && 
      it.get('label') == 'wait for install' &&
      it.get('script').contains("Waiting 30s for initial pod creation...") &&
      it.get('script').contains("sleep 30") &&
      it.get('script').contains('POD_COUNT=') &&
      it.get('script').contains('kubectl get pods') &&
      it.get('script').contains("wc -l") &&
      it.get('script').contains('No pods found matching selector - this chart may only contain jobs/cronjobs') &&
      it.get('script').contains("ImagePullBackOff|ErrImagePull|CrashLoopBackOff|CreateContainerConfigError") &&
      it.get('script').contains("Waiting for pods to be scheduled and ready...") &&
      it.get('script').contains("kubectl wait --for=condition=ready pod") &&
      it.get('script').contains("--timeout=1220s")
    })
    1 * steps.sh('rm aks-debug-info.sh')
  }

  def "installOrUpgrade() on non-PR branch should execute with --wait flag"() {
    given:
    def nonPrSteps = Mock(JenkinsStepMock.class)
    nonPrSteps.env >> [AKS_RESOURCE_GROUP: "cnp-aks-rg",
                       AKS_CLUSTER_NAME: "cnp-aks-cluster",
                       TEAM_NAMESPACE: "cnp",
                       SUBSCRIPTION_NAME: "${SUBSCRIPTION}",
                       BRANCH_NAME: "master"]
    def nonPrHelm = new Helm(nonPrSteps, CHART)

    when:
    nonPrHelm.installOrUpgrade("staging", ["val1", "val2"], ["--namespace cnp"])

    then:
    1 * nonPrSteps.sh({it.containsKey('script') &&
                    it.get('script').contains("helm dependency update ${CHART_PATH}") &&
                    it.containsKey('returnStdout') &&
                    it.get('returnStdout').equals(true)
    })
    1 * nonPrSteps.sh({it.containsKey('script') &&
                    it.get('script').contains("helm lint ${CHART_PATH}  -f val1 -f val2") &&
                    it.containsKey('returnStdout') &&
                    it.get('returnStdout').equals(true)
    })
    1 * nonPrSteps.libraryResource('uk/gov/hmcts/helm/aks-debug-info.sh')
    1 * nonPrSteps.writeFile(_)
    1 * nonPrSteps.sh('chmod +x aks-debug-info.sh')
    1 * nonPrSteps.sh({it.containsKey('label') && 
      it.get('label') == 'helm upgrade' &&
      it.get('script').contains("helm upgrade ${CHART}-staging  ${CHART_PATH}  -f val1 -f val2 --namespace cnp --install --wait --timeout 1250s") &&
      it.get('script').contains("|| ./aks-debug-info.sh ${CHART}-staging cnp")
    })
    0 * nonPrSteps.sh({it.containsKey('label') && it.get('label') == 'wait for install'})
    1 * nonPrSteps.sh('rm aks-debug-info.sh')
  }

  def "delete() should execute with the correct chart and options"() {
    when:
    helm.delete("pr-1", "default")

    then:
    1 * steps.sh({it.containsKey('script') &&
      it.get('script').contains("helm uninstall ${CHART}-pr-1") &&
      it.containsKey('returnStdout') &&
      it.get('returnStdout').equals(true)
    })
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

  // ==================== Dual ACR Publish Tests ====================

  def "dual publish mode is disabled when DUAL_ACR_PUBLISH_ENABLED is not set"() {
    given:
    def testSteps = Mock(JenkinsStepMock.class)
    testSteps.env >> [AKS_RESOURCE_GROUP: "cnp-aks-rg",
                      REGISTRY_NAME: "hmctspublic",
                      REGISTRY_SUBSCRIPTION: "test-sub",
                      SUBSCRIPTION_NAME: "${SUBSCRIPTION}",
                      BRANCH_NAME: "PR-123"]
    
    when:
    def testHelm = new Helm(testSteps, CHART)
    
    then:
    testHelm.isDualPublishEnabled() == false
  }

  def "dual publish mode is disabled when secondary registry details are missing"() {
    given:
    def testSteps = Mock(JenkinsStepMock.class)
    testSteps.env >> [AKS_RESOURCE_GROUP: "cnp-aks-rg",
                      REGISTRY_NAME: "hmctspublic",
                      REGISTRY_SUBSCRIPTION: "test-sub",
                      SUBSCRIPTION_NAME: "${SUBSCRIPTION}",
                      BRANCH_NAME: "PR-123",
                      DUAL_ACR_PUBLISH_ENABLED: "true",
                      SECONDARY_REGISTRY_NAME: null]
    testSteps.echo(_) >> null
    
    when:
    def testHelm = new Helm(testSteps, CHART)
    
    then:
    testHelm.isDualPublishEnabled() == false
  }

  def "dual publish mode is enabled when all secondary registry details are provided"() {
    given:
    def testSteps = Mock(JenkinsStepMock.class)
    testSteps.env >> [AKS_RESOURCE_GROUP: "cnp-aks-rg",
                      REGISTRY_NAME: "hmctspublic",
                      REGISTRY_SUBSCRIPTION: "test-sub",
                      SUBSCRIPTION_NAME: "${SUBSCRIPTION}",
                      BRANCH_NAME: "PR-123",
                      DUAL_ACR_PUBLISH_ENABLED: "true",
                      SECONDARY_REGISTRY_NAME: "hmctsold",
                      SECONDARY_REGISTRY_RESOURCE_GROUP: "hmcts-old-rg",
                      SECONDARY_REGISTRY_SUBSCRIPTION: "old-sub"]
    testSteps.echo(_) >> null
    
    when:
    def testHelm = new Helm(testSteps, CHART)

    then:
    testHelm.isDualPublishEnabled() == true
    testHelm.secondaryRegistryName == "hmctsold"
  }

  // ==================== Cross-registry dependency tests ====================

  def "authenticateAcr() should rewrite external chart dependencies to the current registry in sandbox"() {
    given:
    def sandboxSteps = Mock(JenkinsStepMock.class)
    sandboxSteps.env >> [AKS_RESOURCE_GROUP: "cnp-aks-rg",
                         AKS_CLUSTER_NAME: "cnp-aks-cluster",
                         TEAM_NAMESPACE: "cnp",
                         SUBSCRIPTION_NAME: "sbox",
                         REGISTRY_NAME: "hmctssbox",
                         REGISTRY_SUBSCRIPTION: "DTS-SHAREDSERVICES-SBOX",
                         BRANCH_NAME: "PR-123"]
    sandboxSteps.fileExists("${CHART_PATH}/Chart.yaml") >> true
    sandboxSteps.readFile("${CHART_PATH}/Chart.yaml") >> "dependencies:\n  - name: base\n    repository: oci://hmctsprod.azurecr.io/helm\n"
    def sandboxHelm = new Helm(sandboxSteps, CHART)
    sandboxHelm.acr = Mock(uk.gov.hmcts.contino.azure.Acr)

    when:
    sandboxHelm.authenticateAcr()

    then:
    1 * sandboxHelm.acr.az("acr login --name hmctssbox --subscription DTS-SHAREDSERVICES-SBOX")
    1 * sandboxSteps.writeFile({ Map args ->
      args.file == "${CHART_PATH}/Chart.yaml" &&
        args.text.contains("oci://hmctssbox.azurecr.io/helm") &&
        !args.text.contains("hmctsprod")
    })
    0 * sandboxHelm.acr.az({ it.toString().contains("acr login --name hmctsprod") })
    sandboxHelm.sandboxRewrittenRegistries == ['hmctsprod']
  }

  def "authenticateAcr() should use the configured subscription for external registries outside sandbox"() {
    given:
    def nonprodSteps = Mock(JenkinsStepMock.class)
    nonprodSteps.env >> [AKS_RESOURCE_GROUP: "cnp-aks-rg",
                         AKS_CLUSTER_NAME: "cnp-aks-cluster",
                         TEAM_NAMESPACE: "cnp",
                         SUBSCRIPTION_NAME: "nonprod",
                         REGISTRY_NAME: "hmctspublic",
                         REGISTRY_SUBSCRIPTION: "DCD-CNP-DEV",
                         EXTERNAL_ACR_SUBSCRIPTION_HMCTSPROD: "DCD-CNP-PROD",
                         BRANCH_NAME: "PR-123"]
    nonprodSteps.fileExists("${CHART_PATH}/Chart.yaml") >> true
    nonprodSteps.readFile("${CHART_PATH}/Chart.yaml") >> "dependencies:\n  - name: base\n    repository: oci://hmctsprod.azurecr.io/helm\n"
    def nonprodHelm = new Helm(nonprodSteps, CHART)
    nonprodHelm.acr = Mock(uk.gov.hmcts.contino.azure.Acr)

    when:
    nonprodHelm.authenticateAcr()

    then:
    1 * nonprodHelm.acr.az("acr login --name hmctspublic --subscription DCD-CNP-DEV")
    1 * nonprodHelm.acr.az("acr login --name hmctsprod --subscription DCD-CNP-PROD")
    0 * nonprodSteps.writeFile(_)
  }

  def "installOrUpgrade() should restore original chart metadata after sandbox dependency rewrite"() {
    given:
    def originalChartYaml = "dependencies:\n  - name: base\n    repository: oci://hmctsprod.azurecr.io/helm\n"
    def sandboxSteps = Mock(JenkinsStepMock.class)
    sandboxSteps.env >> [AKS_RESOURCE_GROUP: "cnp-aks-rg",
                         AKS_CLUSTER_NAME: "cnp-aks-cluster",
                         TEAM_NAMESPACE: "cnp",
                         SUBSCRIPTION_NAME: "sbox",
                         REGISTRY_NAME: "hmctssbox",
                         REGISTRY_SUBSCRIPTION: "DTS-SHAREDSERVICES-SBOX",
                         BRANCH_NAME: "master"]
    sandboxSteps.fileExists("${CHART_PATH}/Chart.yaml") >> true
    sandboxSteps.readFile("${CHART_PATH}/Chart.yaml") >> originalChartYaml
    def sandboxHelm = new Helm(sandboxSteps, CHART)
    sandboxHelm.acr = Mock(uk.gov.hmcts.contino.azure.Acr)

    when:
    sandboxHelm.authenticateAcr()
    sandboxHelm.installOrUpgrade("staging", ["val1"], [])

    then:
    1 * sandboxSteps.writeFile({ Map args ->
      args.file == "${CHART_PATH}/Chart.yaml" &&
        args.text.contains("oci://hmctssbox.azurecr.io/helm")
    })
    1 * sandboxSteps.writeFile({ Map args ->
      args.file == "${CHART_PATH}/Chart.yaml" &&
        args.text == originalChartYaml
    })
    1 * sandboxSteps.sh("rm -f ${CHART_PATH}/Chart.lock")
    sandboxHelm.rewrittenChartFileBackups.isEmpty()
    sandboxHelm.rewriteGeneratedFiles.isEmpty()
  }

  def "dependencyUpdate() should explain sandbox rewrites when the update fails"() {
    given:
    def failingSteps = Mock(JenkinsStepMock.class)
    failingSteps.env >> [AKS_RESOURCE_GROUP: "cnp-aks-rg",
                         TEAM_NAMESPACE: "cnp",
                         SUBSCRIPTION_NAME: "sbox",
                         REGISTRY_NAME: "hmctssbox",
                         BRANCH_NAME: "PR-123"]
    failingSteps.sh(_) >> { throw new RuntimeException('pull access denied') }
    def failingHelm = new Helm(failingSteps, CHART)
    failingHelm.sandboxRewrittenRegistries = ['hmctsprod']

    when:
    failingHelm.dependencyUpdate()

    then:
    def exception = thrown(RuntimeException)
    exception.message.contains('rewritten from hmctsprod to hmctssbox')
    exception.message.contains('Publish the dependency charts to hmctssbox')
  }

}
