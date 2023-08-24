def call(String credentialsId, String source, String destination) {
    withCredentials([usernamePassword(credentialsId: credentialsId, passwordVariable: 'STORAGE_ACCOUNT_KEY', usernameVariable: 'STORAGE_ACCOUNT_NAME')]) {
      withEnv(["SOURCE=${source}", "DESTINATION=${destination}"]) {
        sh 'chmod +x /usr/bin/azcopy \
        azcopy --source ${SOURCE} \
          --destination https://${STORAGE_ACCOUNT_NAME}.blob.core.windows.net/${DESTINATION} \
          --dest-key ${STORAGE_ACCOUNT_KEY} \
          --set-content-type --recursive'
      }
    }
}
