def call() {
  withCredentials([string(credentialsId: 'jenkins-github-api-token-secret-text', variable: 'BEARER_TOKEN')]) {
    httpRequest acceptType: 'APPLICATION_JSON', contentType: 'APPLICATION_JSON',
      customHeaders: [[maskValue: true, name: 'Authorization', value: "Bearer ${BEARER_TOKEN}"]], httpMode: 'POST', requestBody: '''{
  "ref": "feature/ROC-3443-pr-preview",
  "description": "Deploying feature/ROC-3443-pr-preview",
  "environment": "preview",
  "required_contexts": [],
  "auto_merge": false,
  "transient_environment": true
}''',
      timeout: 15, url: 'https://api.github.com/repos/hmcts/cmc-claim-store/deployments', validResponseCodes: '200:201'
  }
}
