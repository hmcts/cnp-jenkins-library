package uk.gov.hmcts.pipeline

class LibraryBranchControls {
  def steps
  static def libraryBranchControls

  LibraryBranchControls(steps) {
    this.steps = steps
  }

  def getConfigFilePath() {
    return "resources/uk/gov/hmcts/library/allowed-library-branches.yml"
  }

  def getLibraryBranchControls() {
    libraryBranchControls = steps.readYaml(file: getConfigFilePath())
    return libraryBranchControls
  }

  private String extractLibraryBranch(String libraryReference) {
    if (!libraryReference) {
      return null
    }

    def reference = libraryReference.trim()
    def extractedReference = reference

    // Supports values like @Library("Infrastructure@my-branch")
    def matcher = (reference =~ /@Library\(\s*["']?([^"'\)\s]+)["']?\s*\)/)
    if (matcher.find()) {
      extractedReference = matcher.group(1)
    }

    if (extractedReference.contains('@')) {
      return extractedReference.split('@', 2)[1].trim()
    }

    // If only the library name is provided, default to the configured default branch.
    if (extractedReference.equalsIgnoreCase('Infrastructure')) {
      return 'master'
    }

    return extractedReference
  }

  boolean isBranchAllowed(String libraryBranch, def pipelineConfig = null) {

    def libraryBranchControls = getLibraryBranchControls()
    if (!libraryBranchControls.containsKey('branches')) {

      steps.echo "No 'branches' key found in deployment controls configuration. Deployment will be disabled by default. Contact Platform Operations team."
      return false
    }

    def configuredBranches = libraryBranchControls.get('branches')
    def branchToCheck = extractLibraryBranch(libraryBranch)

    def branchEntry = configuredBranches.find { it.name.equalsIgnoreCase(branchToCheck) }
    def branchAllowed = branchEntry && branchEntry['allowed'] == true

    if (!branchAllowed) {
      steps.echo '''
       ================================================================================
       ____      ____  _       _______     ____  _____  _____  ____  _____   ______
       |_  _|    |_  _|/ \\     |_   __ \\   |_   \\|_   _||_   _||_   \\|_   _|.' ___  |
         \\ \\  /\\  / / / _ \\      | |__) |    |   \\ | |    | |    |   \\ | | / .'   \\_|
         \\ \\/  \\/ / / ___ \\     |  __ /     | |\\ \\| |    | |    | |\\ \\| | | |   ____
           \\  /\\  /_/ /   \\ \\_  _| |  \\ \\_  _| |_\\   |_  _| |_  _| |_\\   |_\\ `.___]  |
           \\/  \\/|____| |____||____| |___||_____|\\____||_____||_____|\\____|`._____.'
      '''

      steps.echo """
        Library branch ${branchToCheck} is not approved for use.
        Make sure to add your branch to:
        - ${getConfigFilePath()} in this repository
        If you recently updated allowed branches and this is unexpected ensure you are using a new agent as this can be cached.
        ================================================================================
      """
    }

    return branchEntry && branchEntry['allowed'] == true
  }
}