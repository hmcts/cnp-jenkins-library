package uk.gov.hmcts.contino

class Environment implements Serializable {
  String environmentName

  Environment(String environmentName) {
    this.environmentName = Objects.requireNonNull(environmentName)
  }

  boolean isProduction() {
    environmentName == 'prod' ||
    environmentName == 'sprod'
  }

  boolean isAATEnvironment() {
    environmentName == 'aat' ||
    environmentName == 'saat'
  }}
