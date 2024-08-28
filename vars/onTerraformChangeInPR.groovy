def call(Closure block) {
  folderExists('infrastructure') {
    writeFile file: 'check-infrastructure-files-changed.sh', text: libraryResource('uk/gov/hmcts/infrastructure/check-infrastructure-files-changed.sh')

    def infraFolderHasChanges = sh(
      script: "chmod +x check-infrastructure-files-changed.sh\n" +
        "    ./check-infrastructure-files-changed.sh",
      returnStatus: true
    )
    sh 'rm check-infrastructure-files-changed.sh'
    if (infraFolderHasChanges == 1) {
      println "Infrastructure folder has changes"
    } else if (infraFolderHasChanges == 0) {
      println "Infrastructure folder has no changes"
    } else {
      println "[WARN] Unexpected return code: ${infraFolderHasChanges}"
    }

    if (infraFolderHasChanges == 1) {
      return block.call()
    }
  }
}
