package uk.gov.hmcts.contino

abstract class AbstractBuilder implements Builder, Serializable {

  def steps
  def gatling

  AbstractBuilder(steps) {
    this.steps = steps
    this.gatling = new Gatling(this.steps)
  }

  @Override
  def performanceTest() {
    this.gatling.execute()
  }

}
