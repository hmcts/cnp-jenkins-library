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
      //sauceconnect(options: "-u divorce -K e0067992-049e-412c-9d15-2566a28cfb73 --verbose --tunnel-identifier reformtunnel", verboseLogging: true)
      yarn("test:crossbrowser")
  }

  def yarn(task){
    steps.sh("yarn ${task}")
  }

  def performanceTest() {
    this.gatling.execute()
  }

}



