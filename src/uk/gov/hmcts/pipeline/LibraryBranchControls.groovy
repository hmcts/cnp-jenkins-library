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

  private String resolveLibraryBranch() {
    def runtimeLibraryBranch = resolveLibraryBranchFromRuntime()
    if (runtimeLibraryBranch) {
      return runtimeLibraryBranch
    }

    if (steps?.env?.SHARED_LIBRARY_VERSION) {
      return steps.env.SHARED_LIBRARY_VERSION
    }

    if (steps?.env?.SHARED_LIBRARY_NAME) {
      return steps.env.SHARED_LIBRARY_NAME
    }

    return 'Infrastructure'
  }

  private String resolveLibraryBranchFromRuntime() {
    try {
      def actionClass = this.class.classLoader.loadClass('org.jenkinsci.plugins.workflow.libs.LibrariesAction')
      def action = steps?.currentBuild?.rawBuild?.getAction(actionClass)
      def envLibraryName = steps?.env?.SHARED_LIBRARY_NAME
      def namesToTry = envLibraryName ? [envLibraryName] : ['Infrastructure', 'Pipeline', 'Tagged']

      for (name in namesToTry) {
        def record = action?.libraries?.find { it.name == name }
        if (record?.version) {
          return record.version
        }
      }

      def firstLoadedLibrary = action?.libraries?.first()
      if (firstLoadedLibrary?.version) {
        return firstLoadedLibrary.version
      }
    } catch (ignored) {
      // Runtime metadata lookup is best-effort; fallback handlers below remain in place.
    }

    return null
  }

  boolean isBranchAllowed(def pipelineConfig = null) {

    def libraryBranchControls = getLibraryBranchControls()
    if (!libraryBranchControls.containsKey('branches')) {

      steps.echo "No 'branches' key found in deployment controls configuration. Deployment will be disabled by default. Contact Platform Operations team."
      return false
    }

    def configuredBranches = libraryBranchControls.get('branches')
    def branchToCheck = extractLibraryBranch(resolveLibraryBranch())

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