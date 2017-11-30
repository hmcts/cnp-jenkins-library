package uk.gov.hmcts.contino

class ProjectBranch {

  String branchName

  ProjectBranch(String branchName) {
    this.branchName = Objects.requireNonNull(branchName)
  }

  boolean isMaster() {
    branchName == 'master' || branchName == 'cnp'
  }

  boolean isPR() {
    branchName.startsWith('PR')
  }

  @Override
  String toString() {
    return branchName
  }

}
