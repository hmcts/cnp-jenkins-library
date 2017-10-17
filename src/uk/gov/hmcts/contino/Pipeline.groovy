package uk.gov.hmcts.contino

class Pipeline {

  public Closure afterCheckoutBody

  void afterCheckout(Closure afterCheckoutBody) {
    System.out.println("afterCheckout Called")
    this.afterCheckoutBody = afterCheckoutBody
  }
}
