package uk.gov.hmcts.contino

class Pipeline {

  public Closure afterCheckoutBody

  void afterCheckout(Closure afterCheckoutBody) {
    this.afterCheckoutBody = afterCheckoutBody
  }
}
