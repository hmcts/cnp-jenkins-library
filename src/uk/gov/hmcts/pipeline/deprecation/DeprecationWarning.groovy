package uk.gov.hmcts.pipeline.deprecation

class DeprecationWarning implements Serializable {

  final String warningKey
  final String warningMessage
  final Date deprecationDate

  DeprecationWarning(String warningKey, String warningMessage, Date deprecationDate) {
    this.warningKey = warningKey
    this.warningMessage = warningMessage
    this.deprecationDate = deprecationDate
  }

}
