package uk.gov.hmcts.contino

class Pipeline {

  Closure afterCheckoutBody

  def afterCheckout(Closure afterCheckoutBody) {
    this.afterCheckoutBody = afterCheckoutBody
  }
}
