package uk.gov.hmcts.contino

abstract class  AbstractNightlyBuilder implements NightlyBuilder, Serializable {

    def steps

  AbstractNightlyBuilder(steps) {
      this.steps = steps
    }

  }


