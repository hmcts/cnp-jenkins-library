def call() {
  httpRequest acceptType: 'APPLICATION_JSON', contentType: 'APPLICATION_JSON',
    customHeaders: [[maskValue: true, name: 'Authorization', value: 'Bearer blah']], httpMode: 'POST', requestBody: '''{
  "state": "success",
  "description": "Deployment finished successfully.",
  "environment_url": "http://cmc-claim-store-preview-staging.service.core-compute-preview.internal"
}''',
    timeout: 15, url: 'https://api.github.com/repos/hmcts/cmc-claim-store/deployments/78043536/statuses', validResponseCodes: '200:201'

}
