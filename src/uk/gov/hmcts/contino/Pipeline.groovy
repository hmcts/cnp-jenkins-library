package uk.gov.hmcts.contino

class Pipeline {

  Closure afterCheckoutBody

  void afterCheckout(Closure afterCheckoutBody) {
    this.afterCheckoutBody = afterCheckoutBody
  }
}
