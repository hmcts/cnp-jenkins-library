def call(Closure block) {
  def credentialsId = env.GIT_CREDENTIALS_ID
  folderExists('infrastructure') {
    writeFile file: 'check-infrastructure-files-changed.sh', text: libraryResource('uk/gov/hmcts/infrastructure/check-infrastructure-files-changed.sh')

    withCredentials([usernamePassword(credentialsId: credentialsId, passwordVariable: 'BEARER_TOKEN', usernameVariable: 'APP_ID')]) {
      def bearerToken = env.BEARER_TOKEN
      def infraFolderHasChanges = sh(
        script: "chmod +x check-infrastructure-files-changed.sh\n" +
          "    ./check-infrastructure-files-changed.sh $credentialsId $bearerToken",
        returnStatus: true
      )
      sh 'rm check-infrastructure-files-changed.sh'
      if (infraFolderHasChanges == 1) {
        return block.call()
      }
    }
  }
}
