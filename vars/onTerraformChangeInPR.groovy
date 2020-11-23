def call(Closure block) {
  folderExists('infrastructure') {
    writeFile file: 'check-infrastructure-files-changed.sh', text: libraryResource('uk/gov/hmcts/infrastructure/check-infrastructure-files-changed.sh')

    INFRA_CHANGED = sh(
      script: "chmod +x check-infrastructure-files-changed.sh\n" +
        "    ./check-infrastructure-files-changed.sh",
      returnStatus: true
    )
    sh 'rm check-infrastructure-files-changed.sh'
    if (INFRA_CHANGED == 1) {
      return block.call()
    }
  }
}
