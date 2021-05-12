package uk.gov.hmcts.pipeline

class AKSSubscription implements Serializable {
  def final name
  def final envName
  def final id
  def final steps
  def final keyvaultName

  AKSSubscription(Object steps, String name, String keyvaultName, String envName, String id) {
    this.keyvaultName = keyvaultName
    this.steps = steps
    this.name = name
    this.envName = envName
    this.id = id
  }
}
