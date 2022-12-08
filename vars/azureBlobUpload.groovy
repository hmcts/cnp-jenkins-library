def call(String credentialsId, String source, String destination) {
  withDocker('hmcts/moj-azcopy-image:7.2.0-netcore-1.0', '-u root') {
    withCredentials([usernamePassword(credentialsId: credentialsId, passwordVariable: 'STORAGE_ACCOUNT_KEY', usernameVariable: 'STORAGE_ACCOUNT_NAME')]) {
      withEnv(["SOURCE=${source}", "DESTINATION=${destination}"]) {
        sh 'azcopy --source ${SOURCE} \
          --destination https://${STORAGE_ACCOUNT_NAME}.blob.core.windows.net/${DESTINATION} \
          --dest-key ${STORAGE_ACCOUNT_KEY} \
          --set-content-type --recursive'
      }
    }
  }
}
