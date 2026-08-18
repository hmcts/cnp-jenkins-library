package uk.gov.hmcts.contino

import groovy.json.JsonSlurperClassic

import java.math.BigDecimal
import java.net.URLEncoder

class AzureVmPricing implements Serializable {

  static final String IMDS_URL =
    'http://169.254.169.254/metadata/instance/compute?api-version=2021-02-01'
  static final String PRICES_URL =
    'https://prices.azure.com/api/retail/prices'

  private final def steps
  private final Map<String, Map<String, Object>> metadataByNode = [:]
  private final Map<String, Map<String, Object>> pricingByKey = [:]

  AzureVmPricing(steps) {
    this.steps = steps
  }

  Map<String, Object> lookup(String nodeName) {
    String nodeCacheKey = nodeName ?: 'unknown'
    Map<String, Object> metadata = metadataByNode[nodeCacheKey]
    if (metadata == null) {
      metadata = fetchMetadata()
      metadataByNode[nodeCacheKey] = metadata
    }

    String pricingCacheKey =
      "${nodeCacheKey}|${metadata.vm_sku}|${metadata.vm_region}"
    Map<String, Object> price = pricingByKey[pricingCacheKey]
    if (price == null) {
      price = fetchPrice(metadata.vm_sku as String, metadata.vm_region as String)
      pricingByKey[pricingCacheKey] = price
    }

    return [
      vm_sku           : metadata.vm_sku,
      vm_region        : metadata.vm_region,
      vm_hourly_price  : price.vm_hourly_price,
      vm_price_currency: price.vm_price_currency
    ]
  }

  private Map<String, Object> fetchMetadata() {
    try {
      def response = steps.httpRequest(
        url: IMDS_URL,
        httpMode: 'GET',
        customHeaders: [[name: 'Metadata', value: 'true', maskValue: false]],
        timeout: 5,
        validResponseCodes: '200'
      )
      def compute = new JsonSlurperClassic()
        .parseText(response.content as String) as Map
      String sku = compute.vmSize?.toString()
      String region = compute.location?.toString()
      String osType = compute.osType?.toString()

      if (!sku || !region || !osType) {
        throw new AzureVmPricingException(
          'IMDS response did not contain vmSize, location, and osType'
        )
      }
      if (!osType.equalsIgnoreCase('Linux')) {
        throw new AzureVmPricingException(
          "IMDS reported unsupported operating system '${osType}'; Linux is required"
        )
      }

      return [vm_sku: sku, vm_region: region]
    } catch (AzureVmPricingException error) {
      throw error
    } catch (RuntimeException error) {
      throw new AzureVmPricingException(
        "IMDS lookup failed: ${error.message ?: error.class.simpleName}",
        error
      )
    }
  }

  private Map<String, Object> fetchPrice(String sku, String region) {
    String filter =
      "serviceName eq 'Virtual Machines' and " +
      "armSkuName eq '${sku}' and " +
      "armRegionName eq '${region}' and " +
      "priceType eq 'Consumption'"
    String encodedFilter = URLEncoder.encode(filter, 'UTF-8')
    String url = "${PRICES_URL}?api-version=2023-01-01-preview&%24filter=${encodedFilter}"

    try {
      def response = steps.httpRequest(
        url: url,
        httpMode: 'GET',
        timeout: 10,
        validResponseCodes: '200'
      )
      def payload = new JsonSlurperClassic()
        .parseText(response.content as String) as Map
      List<Map> items = ((payload.Items ?: payload.items ?: []) as List)
      Map selected = items.find { Map item ->
        String itemSku = item.skuName?.toString()
        String productName = item.productName?.toString() ?: ''
        item.armSkuName?.toString() == sku &&
          itemSku == sku &&
          item.isPrimaryMeterRegion == true &&
          !productName.toLowerCase().contains('windows') &&
          !itemSku.toLowerCase().contains('spot') &&
          !itemSku.toLowerCase().contains('low priority')
      }

      if (selected == null) {
        throw new AzureVmPricingException(
          "No eligible Linux consumption price found for SKU '${sku}' in '${region}'"
        )
      }

      return [
        vm_hourly_price  : new BigDecimal(selected.retailPrice.toString()),
        vm_price_currency: selected.currencyCode?.toString()
      ]
    } catch (AzureVmPricingException error) {
      throw error
    } catch (RuntimeException error) {
      throw new AzureVmPricingException(
        "Retail price lookup failed: ${error.message ?: error.class.simpleName}",
        error
      )
    }
  }
}
