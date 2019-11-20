package uk.gov.hmcts.contino

class ProjectBranch implements Serializable {

  String branchName

  ProjectBranch(String branchName) {
    this.branchName = Objects.requireNonNull(branchName)
  }

  boolean isMaster() {
    branchName == 'master'
  }

  boolean isPR() {
    branchName.startsWith('PR')
  }

  boolean isDemo() {
    branchName == 'demo'
  }

  boolean isHMCTSDemo() {
    branchName == 'hmctsdemo'
  }

  boolean isPerftest() {
    branchName == 'perftest'
  }

  boolean isEthosldata() {
    branchName == 'ethosldata'
  }

  boolean isIthc() {
    branchName == 'ithc'
  }

  String deploymentNamespace() {
    // lowercase because some Azure resource names require lowercase
    return (isPR()) ? branchName.toLowerCase() : ""
  }

  String imageTag() {
    if (isPR()) {
      return branchName.toLowerCase()
    }
    return (branchName == 'master') ? 'staging' : this.branchName.replaceAll('/', '-')
  }

}
