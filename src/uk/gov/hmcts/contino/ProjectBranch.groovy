package uk.gov.hmcts.contino

class ProjectBranch implements Serializable {

  String branchName

  ProjectBranch(String branchName) {
    this.branchName = Objects.requireNonNull(branchName)
  }

  boolean isMaster() {
    branchName == 'master' ||
    branchName == 'cnp' ||
    branchName == 'masterv2'
  }

  boolean isPR() {
    branchName.startsWith('PR')
  }

  boolean isDemo() {
    branchName == 'demo'
  }

  boolean isHMCTSDemo() {
    branchName.startsWith('hmctsdemo')
  }

  String deploymentNamespace() {
    // lowercase because some Azure resource names require lowercase
    return (isPR() || isHMCTSDEMO() ? branchName.toLowerCase() : ""
  }

  String imageTag() {
    if (isPR() || isHMCTSDEMO()) {
      return branchName.toLowerCase()
    }
    return (branchName == 'master' || branchName == 'masterv2') ? 'latest' : this.branchName.replaceAll('/', '-')
  }

}
