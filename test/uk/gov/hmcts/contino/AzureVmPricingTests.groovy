package uk.gov.hmcts.contino

import spock.lang.Specification

class AzureVmPricingTests extends Specification {

  def steps
  def pricing

  def setup() {
    steps = Mock(JenkinsStepMock)
    pricing = new AzureVmPricing(steps)
  }

  def "returns Linux VM metadata and the exact pay-as-you-go meter"() {
    given:
    steps.httpRequest({ it.url == AzureVmPricing.IMDS_URL }) >> [
      content: '{"vmSize":"Standard_D2s_v5","location":"uksouth","osType":"Linux","resourceId":"/subscriptions/1/resourceGroups/rg/providers/Microsoft.Compute/virtualMachines/agent-1"}'
    ]
    steps.httpRequest({ it.url.startsWith(AzureVmPricing.PRICES_URL) }) >> [
      content: '''{
        "Items": [
          {
            "armSkuName": "Standard_D2s_v5",
            "skuName": "Standard_D2s_v5",
            "productName": "Virtual Machines Dsv5 Series",
            "isPrimaryMeterRegion": true,
            "retailPrice": 0.111,
            "currencyCode": "USD"
          },
          {
            "armSkuName": "Standard_D2s_v5",
            "skuName": "Standard_D2s_v5",
            "productName": "Virtual Machines Dsv5 Series Windows",
            "isPrimaryMeterRegion": true,
            "retailPrice": 0.203,
            "currencyCode": "USD"
          },
          {
            "armSkuName": "Standard_D2s_v5",
            "skuName": "Standard_D2s_v5 Spot",
            "productName": "Virtual Machines Dsv5 Series",
            "isPrimaryMeterRegion": true,
            "retailPrice": 0.014852,
            "currencyCode": "USD"
          }
        ]
      }'''
    ]

    when:
    def result = pricing.lookup('agent-1')

    then:
    result.vm_sku == 'Standard_D2s_v5'
    result.vm_region == 'uksouth'
    result.vm_hourly_price.compareTo(new BigDecimal('0.111')) == 0
    result.vm_price_currency == 'USD'
  }

  def "caches metadata and pricing for the same node"() {
    given:
    1 * steps.httpRequest({ it.url == AzureVmPricing.IMDS_URL }) >> [
      content: '{"vmSize":"Standard_D2s_v5","location":"uksouth","osType":"Linux"}'
    ]
    1 * steps.httpRequest({ it.url.startsWith(AzureVmPricing.PRICES_URL) }) >> [
      content: '''{
        "Items": [{
          "armSkuName": "Standard_D2s_v5",
          "skuName": "Standard_D2s_v5",
          "productName": "Virtual Machines Dsv5 Series",
          "isPrimaryMeterRegion": true,
          "retailPrice": 0.111,
          "currencyCode": "USD"
        }]
      }'''
    ]

    when:
    pricing.lookup('agent-1')
    pricing.lookup('agent-1')

    then:
    noExceptionThrown()
  }

  def "rejects a non-Linux metadata response"() {
    given:
    steps.httpRequest({ it.url == AzureVmPricing.IMDS_URL }) >> [
      content: '{"vmSize":"Standard_D2s_v5","location":"uksouth","osType":"Windows"}'
    ]

    when:
    pricing.lookup('agent-1')

    then:
    def error = thrown(AzureVmPricingException)
    error.message.contains('Linux')
  }

  def "reports an IMDS request failure"() {
    given:
    steps.httpRequest({ it.url == AzureVmPricing.IMDS_URL }) >> {
      throw new RuntimeException('connection refused')
    }

    when:
    pricing.lookup('agent-1')

    then:
    def error = thrown(AzureVmPricingException)
    error.message.contains('IMDS')
    error.message.contains('connection refused')
  }
}
