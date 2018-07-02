package uk.gov.hmcts.contino

class NightlyPipelineBuilder implements NightlyBuilder {

  def gatling

  NightlyPipelineBuilder(steps) {
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



