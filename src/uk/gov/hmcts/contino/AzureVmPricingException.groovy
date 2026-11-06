package uk.gov.hmcts.contino

class AzureVmPricingException extends RuntimeException {

  AzureVmPricingException(String message) {
    super(message)
  }

  AzureVmPricingException(String message, Throwable cause) {
    super(message, cause)
  }
}
