package uk.gov.hmcts.contino

class ProjectBranch implements Serializable {

  String branchName

  ProjectBranch(String branchName) {
    this.branchName = Objects.requireNonNull(branchName)
  }

  boolean isMaster() {
    branchName == 'master' ||
    branchName.startsWith('cnp-') ||
    branchName == 'cnp'
  }

  boolean isPR() {
    branchName.startsWith('PR')
  }

}
