package uk.gov.hmcts.contino

class Pipeline implements Serializable {

  public Closure afterCheckoutBody

  void afterCheckout(Closure afterCheckoutBody) {
    System.out.println("afterCheckout Called")
    this.afterCheckoutBody = afterCheckoutBody
  }
}
