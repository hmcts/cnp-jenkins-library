package uk.gov.hmcts.contino

import spock.lang.Specification
import static org.assertj.core.api.Assertions.*

class KubectlTest extends Specification {

  static final String NAMESPACE = 'cnp'
  static final String SUBSCRIPTION = 'sandbox'
  static final String DEPLOYMENT = 'my-deployment'

  def steps
  def kubectl

  def setup() {
    steps = Mock(JenkinsStepMock.class)
    steps.env >> [AKS_RESOURCE_GROUP: "cnp-aks-rg",
                  AKS_CLUSTER_NAME: "cnp-aks-cluster"]
    kubectl = new Kubectl(steps, SUBSCRIPTION, NAMESPACE)
  }

  def "createNamespace() should execute with the correct namespace"() {
    when:
      kubectl.createNamespace(NAMESPACE)

    then:
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("kubectl create namespace ${NAMESPACE}") &&
                    it.containsKey('returnStatus') &&
                    it.get('returnStatus').equals(true)})
  }

  def "login() should authenticate using the correct resource group and cluster name"() {
    when:
      kubectl.login()

    then:
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("aks get-credentials --resource-group cnp-aks-rg --name cnp-aks-cluster")})
  }

  def "login() should use the subscription passed in"() {
    when:
      kubectl.login()

    then:
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("env AZURE_CONFIG_DIR=/opt/jenkins/.azure-${SUBSCRIPTION}")})
  }

  def "apply() should have namespace and NO JSON output"() {
    when:
      kubectl.apply 'deployment.yaml'

    then:
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("kubectl apply -f deployment.yaml -n ${NAMESPACE}") &&
                    !(it.get('script').contains('-o json')) &&
                    it.containsKey('returnStdout') &&
                    it.get('returnStdout').equals(true)})
  }

  def "delete() should have namespace and NO JSON output"() {
    when:
      kubectl.delete 'deployment.yaml'

    then:
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("kubectl delete -f deployment.yaml -n ${NAMESPACE}") &&
                    !(it.get('script').contains('-o json')) &&
                    it.containsKey('returnStdout') &&
                    it.get('returnStdout').equals(true)})
  }

  def "deleteDeployment() should have deployment, namespace and return exit status"() {
    when:
      kubectl.deleteDeployment(DEPLOYMENT)

    then:
      1 * steps.sh({it.containsKey('script') &&
        it.get('script').contains("kubectl delete deployment ${DEPLOYMENT} -n ${NAMESPACE}") &&
        it.containsKey('returnStatus') &&
        it.get('returnStatus').equals(true)})
  }

  def "getService() should have namespace and JSON output"() {
    when:
      kubectl.getService 'frontend-ilb'

    then:
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("kubectl get service frontend-ilb -n ${NAMESPACE} -o json") &&
                    it.containsKey('returnStdout') &&
                    it.get('returnStdout').equals(true)})
  }

  def "getServiceLoadbalancerIP() should throw exception if IP never becomes available"() {
    when:
      kubectl = Spy(Kubectl, constructorArgs:[steps, SUBSCRIPTION, NAMESPACE])
      kubectl.getService('custard-recipe-backend-ilb') >> getServiceJsonWithUninitialisedILB()
      kubectl.getServiceLoadbalancerIP('custard-recipe-backend-ilb')

    then:
      thrown RuntimeException
  }

  def "getServiceLoadbalancerIP() return IP address with initialisation"() {
    when:
      kubectl = Spy(Kubectl, constructorArgs:[steps, SUBSCRIPTION, NAMESPACE])
      kubectl.getService('custard-recipe-backend-ilb') >>> [getServiceJsonWithUninitialisedILB(), getServiceJsonWithIP()]
      def ip = kubectl.getServiceLoadbalancerIP('custard-recipe-backend-ilb')

    then:
      assertThat(ip).isEqualTo('172.15.4.97')
  }

  def getServiceJsonWithIP(){ ''' \
{
    "status": {
        "loadBalancer": {
            "ingress": [
                {
                    "ip": "172.15.4.97"
                }
            ]
        }
    }
}
'''
  }

  def getServiceJsonWithUninitialisedILB() { ''' \
{
    "status": {
        "loadBalancer": {}
    }
}

'''

  }
}
