package uk.gov.hmcts.pipeline

class TechStackPublisher {

  def steps
  private final boolean ignoreErrors

  TechStackPublisher(steps) {
    this(steps, true)
  }

  TechStackPublisher(steps, ignoreErrors) {
    this.steps = steps
    this.ignoreErrors = ignoreErrors
  }

  /**
   * @param codeBaseType string indicating which type of report it is, e.g. java or node
   * @param report provider specific report should be a groovy object that can be converted to json.
   */
  def publishTechStackReport(String codeBaseType, report) {

  }

}
