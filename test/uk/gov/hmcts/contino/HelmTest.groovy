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
                  SUBSCRIPTION_NAME: "${SUBSCRIPTION}"]
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

  def "upgrade() should execute with the correct chart and values"() {
    when:
    helm.installOrUpgrade("pr-1", ["val1", "val2"], null)

    then:
    1 * steps.sh({it.containsKey('script') &&
      it.get('script').contains("helm upgrade ${CHART}-pr-1 ${CHART_PATH}  -f val1 -f val2 --install --wait") &&
      it.containsKey('returnStdout') &&
      it.get('returnStdout').equals(true)
    })
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

}
