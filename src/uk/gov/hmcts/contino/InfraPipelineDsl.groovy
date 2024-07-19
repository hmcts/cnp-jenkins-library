package uk.gov.hmcts.contino

class InfraPipelineDsl extends CommonPipelineDsl implements Serializable {

  def final config
  def final steps

  InfraPipelineDsl(steps, callbacks, config) {
    super(steps, callbacks, config)
    this.config = config
    this.steps = steps
  }

}
