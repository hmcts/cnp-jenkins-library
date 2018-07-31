package uk.gov.hmcts.contino

import spock.lang.Specification

class KubectlTest extends Specification {

  def namespace = 'cnp'
  def steps
  def kubectl

  def setup() {
    steps = Mock(JenkinsStepMock.class)
    kubectl = new Kubectl(steps, namespace)
  }

  def "apply() should have namespace and NO JSON output"() {
    when:
      kubectl.apply 'deployment.yaml'

    then:
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains('kubectl apply -f deployment.yaml -n cnp') &&
                    !(it.get('script').contains('-o json')) &&
                    it.containsKey('returnStdout') &&
                    it.get('returnStdout').equals(true)})
  }

  def "delete() should have namespace and NO JSON output"() {
    when:
    kubectl.delete 'deployment.yaml'

    then:
    1 * steps.sh({it.containsKey('script') &&
      it.get('script').contains('kubectl delete -f deployment.yaml -n cnp') &&
      !(it.get('script').contains('-o json')) &&
      it.containsKey('returnStdout') &&
      it.get('returnStdout').equals(true)})
  }

  def "getService() should have namespace and JSON output"() {
    when:
      kubectl.getService 'frontend-ilb'

    then:
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains('kubectl get service frontend-ilb -n cnp -o json') &&
                    it.containsKey('returnStdout') &&
                    it.get('returnStdout').equals(true)})
  }
}
