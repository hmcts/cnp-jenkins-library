package uk.gov.hmcts.pipeline
import groovy.json.JsonSlurperClassic

class LibraryBranchControls {
  def steps
  static def libraryBranchControls

  LibraryBranchControls(steps) {
    this.steps = steps
  }

  def getConfigFilePath() {
    return "uk/gov/hmcts/library/allowed-library-branches.yml"
  }

  def getLibraryBranchControls() {
    def response = steps.httpRequest(
      consoleLogResponseBody: true,
      authentication: steps.env.GIT_CREDENTIALS_ID,
      timeout: 10,
      url: "https://raw.githubusercontent.com/hmcts/cnp-jenkins-library/master/resources/${getConfigFilePath()}",
      validResponseCodes: '200'
    )
    libraryBranchControls = steps.readYaml(text: response.content)
    return libraryBranchControls
  }

  def getLibraryTags() {
    def response = steps.httpRequest(
      consoleLogResponseBody: true,
      authentication: steps.env.GIT_CREDENTIALS_ID,
      timeout: 10,
      url: "https://api.github.com/repos/hmcts/cnp-jenkins-library/tags",
      validResponseCodes: '200'
    )

    def responseContent = response?.content
    if (!responseContent) {
      steps.echo 'No library tags found in the response from GitHub.'
      return []
    }
    try {
      def tagsJson = new JsonSlurperClassic().parseText(responseContent)
      if (!(tagsJson instanceof List)) {
        steps.echo 'Library tags JSON from GitHub is not a list.'
        return []
      }
      return tagsJson
        .findAll { tagEntry -> tagEntry instanceof Map }
        .collect { tagEntry -> tagEntry.name }
        .findAll { it ==~ /\d+\.\d+\.\d+/ }

    } catch (ignored) {
      steps.echo 'Failed to parse library tags JSON from GitHub.'
      return []
    }
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
    if (steps?.env?.PROD_SUBSCRIPTION_NAME == 'sandbox') {
      steps.echo 'Skipping library branch allowlist validation on sandbox Jenkins.'
      return true
    }

    def libraryBranchControls = getLibraryBranchControls()
    if (!libraryBranchControls.containsKey('branches')) {

      steps.echo "No 'branches' key found in deployment controls configuration. Deployment will be disabled by default."
      return false
    }

    def configuredBranches = libraryBranchControls.get('branches')
    def libraryTags = getLibraryTags()
    def branchToCheck = extractLibraryBranch(resolveLibraryBranch())


    def branchEntry = configuredBranches.find { it.name.equalsIgnoreCase(branchToCheck) }
    def tagEntry = libraryTags.find { it.equalsIgnoreCase(branchToCheck) }
    def branchAllowed = branchEntry && branchEntry['allowed'] == true
    def branchOrTagAllowed = branchAllowed || tagEntry != null

    if (!branchOrTagAllowed) {
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
        Library branch/tag: `${branchToCheck}` is not approved for use.
        If you are using a branch, make sure to add it to:
        - resources/${getConfigFilePath()} in hmcts/cnp-jenkins-library
        If you recently updated the allowed branches and this is unexpected, ensure you are using a new agent as this can be cached.
        If a version tag is being used, ensure it exists in the repository and follows semantic versioning (e.g. 1.2.3).
        ================================================================================
      """
    }

    return branchOrTagAllowed
  }
}
