/** Upload a prepared CI evidence directory using the agent's managed identity. */
def call(String subscription, String storageAccountName, String source, String destination) {
  withSubscriptionLogin(subscription) {
    withEnv([
      "EVIDENCE_SOURCE=${source}",
      "EVIDENCE_ACCOUNT=${storageAccountName}",
      "EVIDENCE_DESTINATION=${destination}"
    ]) {
      sh '''
        set -eu
        export AZCOPY_AUTO_LOGIN_TYPE=MSI
        azcopy cp "$EVIDENCE_SOURCE" \
          "https://${EVIDENCE_ACCOUNT}.blob.core.windows.net/${EVIDENCE_DESTINATION}" \
          --recursive=true \
          --overwrite=ifSourceNewer
      '''
    }
  }
}
