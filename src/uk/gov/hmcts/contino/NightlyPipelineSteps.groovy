package uk.gov.hmcts.contino

class NightlyPipelineSteps extends AbstractNightlyBuilder {

  def gatling

  NightlyPipelineSteps(steps) {
    super(steps)
    this.gatling = new Gatling(this.steps)
  }

  def build() {
    yarn("install")
  }

  def crossBrowserTest() {
      yarn("test:crossbrowser")
  }

  def yarn(task){
    steps.sh("yarn ${task}")
  }

  def performanceTest() {
    this.gatling.execute()
  }

}



