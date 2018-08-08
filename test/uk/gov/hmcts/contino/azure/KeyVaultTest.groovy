package uk.gov.hmcts.contino.azure

import spock.lang.Specification
import uk.gov.hmcts.contino.JenkinsStepMock
import static org.assertj.core.api.Assertions.*

class KeyVaultTest extends Specification {

  static final String SUBSCRIPTION = 'sandbox'
  static final String VAULT_NAME   = 'myvault'
  static final String KEY          = 'my-secret-key'
  static final String VALUE        = 'my-secret-value'

  def steps
  def keyvault

  void setup() {
    steps = Mock(JenkinsStepMock.class)
    keyvault = new KeyVault(steps, SUBSCRIPTION, VAULT_NAME)
  }

  def "constructor without subscription should set subscription to 'jenkins'"() {
    when:
      keyvault = new KeyVault(steps, VAULT_NAME)

    then:
      assertThat(keyvault.subscription).isEqualTo('jenkins')
  }

  def "store() should set secret with vault name, secret name and value"() {
    when:
      keyvault.store(KEY, VALUE)

    then:
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("keyvault secret set --vault-name '${VAULT_NAME}' --name '${KEY}' --value '${VALUE}'") &&
                    it.containsKey('returnStdout') &&
                    it.get('returnStdout').equals(true)})
  }

  def "find() should show secret with vault name and key name"() {
    when:
      keyvault.find(KEY)

    then:
      1 * steps.sh({it.containsKey('script') &&
                    it.get('script').contains("keyvault secret show --vault-name '${VAULT_NAME}' --name '${KEY}' --query value -o tsv") &&
                    it.containsKey('returnStdout') &&
                    it.get('returnStdout').equals(true)})
  }
}
