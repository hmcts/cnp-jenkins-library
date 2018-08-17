package uk.gov.hmcts.contino

abstract class AbstractBuilder implements Builder, Serializable {

  def steps
  def gatling
  def zapScan

  AbstractBuilder(steps) {
    this.steps = steps
    this.gatling = new Gatling(this.steps)
    this.zapScan = new ZapScan(this.steps)
  }

  @Override
  def performanceTest() {
    this.gatling.execute()
  }

  @Override
  def zapScan(){
    this.zapScan.execute()
  }

}
