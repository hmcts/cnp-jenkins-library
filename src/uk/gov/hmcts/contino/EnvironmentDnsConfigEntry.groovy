package uk.gov.hmcts.contino

class EnvironmentDnsConfigEntry {
    def environment
    def subscription
    def resourceGroup
    def zone
    def ttl
    def active


  @Override
  public String toString() {
    return "EnvironmentDnsConfigEntry{" +
      "environment=" + environment +
      ", subscription=" + subscription +
      ", resourceGroup=" + resourceGroup +
      ", zone=" + zone +
      ", ttl=" + ttl +
      ", active=" + active +
      '}';
  }
}
