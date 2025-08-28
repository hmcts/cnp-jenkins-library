def call(String subscription, String credentialsId, String source, String destination) {
    withCredentials([usernamePassword(credentialsId: credentialsId, passwordVariable: 'STORAGE_ACCOUNT_KEY', usernameVariable: 'STORAGE_ACCOUNT_NAME')]) {
      withSubscriptionLogin(subscription) {
       {
          sh 'export AZCOPY_AUTO_LOGIN_TYPE=MSI && \
          azcopy cp ${SOURCE} \
            https://${STORAGE_ACCOUNT_NAME}.blob.core.windows.net/${DESTINATION} \
            --recursive=true'
        }
      }
    }
}
