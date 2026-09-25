package withPipeline

class DefaultHttpResponseTest {

  static def response = [
    'content': '{"azure_subscription": "fake_subscription_name","azure_client_id": "fake_client_id",' +
      '"azure_client_secret": "fake_secret","azure_tenant_id": "fake_tenant_id"}'
  ]
}