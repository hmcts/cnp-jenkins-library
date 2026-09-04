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

  // ==================== kubeconform() Tests ====================

  def "kubeconform() validates base values with strict validation flags and default k8s version"() {
    given:
    steps.findFiles([glob: "${CHART_PATH}/values.*.template.yaml"]) >> []

    when:
    helm.kubeconform()

    then:
    1 * steps.sh({it.containsKey('label') &&
      it.get('label') == 'kubeconform schema validation (base values)' &&
      it.get('script').contains("helm template ${CHART} ${CHART_PATH}") &&
      it.get('script').contains("-f ${CHART_PATH}/values.yaml") &&
      it.get('script').contains('| kubeconform') &&
      it.get('script').contains('-strict') &&
      it.get('script').contains('-summary') &&
      it.get('script').contains('-ignore-missing-schemas') &&
      it.get('script').contains('-kubernetes-version 1.35.0') &&
      it.get('script').contains('-schema-location default') &&
      !it.get('script').contains('datreeio')
    })
  }

  def "kubeconform() passes a custom k8sVersion through to kubeconform"() {
    given:
    steps.findFiles([glob: "${CHART_PATH}/values.*.template.yaml"]) >> []

    when:
    helm.kubeconform("1.30.0")

    then:
    1 * steps.sh({it.containsKey('label') &&
      it.get('label') == 'kubeconform schema validation (base values)' &&
      it.get('script').contains('-kubernetes-version 1.30.0') &&
      !it.get('script').contains('-kubernetes-version 1.35.0')
    })
  }

  def "kubeconform() validates each documented environment template separately with base values"() {
    given:
    steps.findFiles([glob: "${CHART_PATH}/values.*.template.yaml"]) >> [
      [name: 'values.preview.template.yaml', path: "${CHART_PATH}/values.preview.template.yaml"],
      [name: 'values.aat.template.yaml', path: "${CHART_PATH}/values.aat.template.yaml"]
    ]

    when:
    helm.kubeconform()

    then:
    1 * steps.sh({it.get('label') == 'kubeconform schema validation (base values)' &&
      it.get('script').contains("-f ${CHART_PATH}/values.yaml") &&
      !it.get('script').contains('.template.yaml')
    })
    1 * steps.sh({it.get('label') == 'kubeconform schema validation (values.aat.template.yaml)' &&
      it.get('script').contains("-f ${CHART_PATH}/values.yaml -f ${CHART_PATH}/values.aat.template.yaml") &&
      !it.get('script').contains('values.preview.template.yaml')
    })
    1 * steps.sh({it.get('label') == 'kubeconform schema validation (values.preview.template.yaml)' &&
      it.get('script').contains("-f ${CHART_PATH}/values.yaml -f ${CHART_PATH}/values.preview.template.yaml") &&
      !it.get('script').contains('values.aat.template.yaml')
    })
  }

  def "kubeconform() validates environment templates in path order"() {
    given:
    def validationLabels = []
    steps.findFiles([glob: "${CHART_PATH}/values.*.template.yaml"]) >> [
      [name: 'values.preview.template.yaml', path: "${CHART_PATH}/values.preview.template.yaml"],
      [name: 'values.aat.template.yaml', path: "${CHART_PATH}/values.aat.template.yaml"]
    ]
    steps.sh(_) >> { Map arguments -> validationLabels.add(arguments.label) }

    when:
    helm.kubeconform()

    then:
    validationLabels == [
      'kubeconform schema validation (base values)',
      'kubeconform schema validation (values.aat.template.yaml)',
      'kubeconform schema validation (values.preview.template.yaml)'
    ]
  }

  def "kubeconform() ignores undocumented multi-part values templates"() {
    given:
    steps.findFiles([glob: "${CHART_PATH}/values.*.template.yaml"]) >> [
      [name: 'values.enableWA.preview.template.yaml', path: "${CHART_PATH}/values.enableWA.preview.template.yaml"],
      [name: 'values.ccd.preview.template.yaml', path: "${CHART_PATH}/values.ccd.preview.template.yaml"]
    ]

    when:
    helm.kubeconform()

    then:
    1 * steps.sh({it.containsKey('label') &&
      it.get('label') == 'kubeconform schema validation (base values)' &&
      !it.get('script').contains('.template.yaml')
    })
    0 * steps.sh({it.get('label').contains('.template.yaml')})
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
      it.get('script').contains("Waiting for initial pod creation...") &&
      it.get('script').contains('timeout 60 kubectl get pods -n cnp -l app.kubernetes.io/instance=my-chart-pr-1,' + "'!job-name'" + ' -w 2>/dev/null | grep -m1 "Running\\|Pending" > /dev/null') &&
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

}
