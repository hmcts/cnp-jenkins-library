def call(String credentialsId, String source, String destination) {

  withCredentials([usernamePassword(credentialsId: "${credentialsId}", passwordVariable: 'STORAGE_ACCOUNT_KEY', usernameVariable: 'STORAGE_ACCOUNT_NAME')]) {
    sh "azcopy --source ${source} \
               --destination https://${STORAGE_ACCOUNT_NAME}.blob.core.windows.net/${destination} \
               --dest-key ${STORAGE_ACCOUNT_KEY} \
               --recursive"
  }

}
