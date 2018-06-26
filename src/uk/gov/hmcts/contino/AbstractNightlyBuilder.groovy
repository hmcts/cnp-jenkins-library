package uk.gov.hmcts.contino

class AbstractNightlyBuilder implements NightlyBuilder, Serializable {

    def steps
    def gatling

  AbstractNightlyBuilder(steps) {
      this.steps = steps
      this.gatling = new Gatling(this.steps)
    }

    @Override
    def performanceTest() {
      this.gatling.execute()
    }

  }


